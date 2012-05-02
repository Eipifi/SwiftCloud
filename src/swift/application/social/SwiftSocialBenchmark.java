package swift.application.social;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

import swift.client.SwiftImpl;
import swift.crdt.interfaces.CachePolicy;
import swift.crdt.interfaces.IsolationLevel;
import swift.crdt.interfaces.Swift;
import swift.dc.DCConstants;
import sys.Sys;

/**
 * Benchmark of SwiftSocial, based on data model derived from WaltSocial
 * prototype [Sovran et al. OSDI 2011].
 * <p>
 * Runs in parallel SwiftSocial sessions from the provided file. Sessions can be
 * distributed among different instances by specifying sessions range.
 */
public class SwiftSocialBenchmark {
    private static String dcName;
    private static String fileName = "scripts/commands.txt";
    private static IsolationLevel isolationLevel;
    private static CachePolicy cachePolicy;
    private static boolean subscribeUpdates;
    private static PrintStream bufferedOutput;
    private static boolean asyncCommit;
    private static int firstSession;
    private static int sessionsNumber;
    private static long cacheEvictionTimeMillis;
    private static boolean inputUsernames;
    private static long thinkTime;

    public static void main(String[] args) {
        if (args.length != 9) {
            System.out
                    .println("Usage: <surrogate addr> <isolationLevel> <cachePolicy> <cache time eviction ms> <subscribe updates (true|false)> <async commit (true|false)>");
            System.out.println("       <think time ms> <input filename> <init database only (true|false)>>");
            System.out.println("With the last option being true, input is treated as list of users to populate db.");
            System.out.println("Without the last options, input is treated as list of sessions with commands to run.");
            return;
        } else {
            dcName = args[0];
            isolationLevel = IsolationLevel.valueOf(args[1]);
            cachePolicy = CachePolicy.valueOf(args[2]);
            cacheEvictionTimeMillis = Long.valueOf(args[3]);
            subscribeUpdates = Boolean.parseBoolean(args[4]);
            asyncCommit = Boolean.parseBoolean(args[5]);
            thinkTime = Long.valueOf(args[6]);
            fileName = args[7];
            inputUsernames = Boolean.valueOf(args[8]);
        }
        Sys.init();

        if (inputUsernames) {
            System.out.println("Populating db with users...");
            final SwiftImpl swiftClient = SwiftImpl.newInstance(dcName, DCConstants.SURROGATE_PORT);
            final SwiftSocial socialClient = new SwiftSocial(swiftClient, isolationLevel, cachePolicy, false, false);
            SwiftSocialMain.initUsers(swiftClient, socialClient, fileName);
            swiftClient.stop(true);
            System.out.println("Finished populating db with users.");
        } else {

            bufferedOutput = new PrintStream(System.out, false);
            bufferedOutput.println("session_id,command,command_exec_time,time");

            // Read sessions from assigned range.
            final List<List<String>> sessions = readSessionsCommands(fileName, 0, Integer.MAX_VALUE);
            final List<Thread> threads = new LinkedList<Thread>();

            // Kick off all sessions.
            for (int i = 0; i < sessions.size(); i++) {
                final int sessionId = i;
                final List<String> commands = sessions.get(i);
                final Thread sessionThread = new Thread() {
                    public void run() {
                        runClientSession(sessionId, commands);
                    }
                };
                sessionThread.start();
                threads.add(sessionThread);
            }

            // Wait for all sessions.
            for (final Thread thread : threads) {
                try {
                    thread.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        System.exit(0);
    }

    private static void runClientSession(final int sessionId, final List<String> commands) {
        Swift swiftCLient = SwiftImpl.newInstance(dcName, DCConstants.SURROGATE_PORT, SwiftImpl.DEFAULT_TIMEOUT_MILLIS,
                cacheEvictionTimeMillis);
        SwiftSocial socialClient = new SwiftSocial(swiftCLient, isolationLevel, cachePolicy, subscribeUpdates,
                asyncCommit);

        final long sessionStartTime = System.currentTimeMillis();
        for (String cmdLine : commands) {
            final long txnStartTime = System.currentTimeMillis();
            String[] toks = cmdLine.split(";");
            final Commands cmd = Commands.valueOf(toks[0].toUpperCase());
            switch (cmd) {
            case LOGIN:
                if (toks.length == 3) {
                    socialClient.login(toks[1], toks[2]);
                    break;
                }
            case LOGOUT:
                if (toks.length == 2) {
                    socialClient.logout(toks[1]);
                    break;
                }
            case READ:
                if (toks.length == 2) {
                    socialClient.read(toks[1], new HashSet<Message>(), new HashSet<Message>());
                    break;
                }
            case SEE_FRIENDS:
                if (toks.length == 2) {
                    socialClient.readFriendList(toks[1]);
                    break;
                }
            case FRIEND:
                if (toks.length == 2) {
                    socialClient.befriend(toks[1]);
                    break;
                }
            case STATUS:
                if (toks.length == 2) {
                    socialClient.updateStatus(toks[1], System.currentTimeMillis());
                    break;
                }
            case POST:
                if (toks.length == 3) {
                    socialClient.postMessage(toks[1], toks[2], System.currentTimeMillis());
                    break;
                }
            default:
                System.err.println("Can't parse command line :" + cmdLine);
                System.err.println("Exiting...");
                System.exit(1);
            }
            final long now = System.currentTimeMillis();
            final long txnExecTime = now - txnStartTime;
            final String log = String.format("%d,%s,%d,%d", sessionId, cmd, txnExecTime, now);
            bufferedOutput.println(log);

            // TODO: Do not wait constant time, use a random distribution.
            if (thinkTime > 0) {
                try {
                    Thread.sleep(thinkTime);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        swiftCLient.stop(true);

        final long now = System.currentTimeMillis();
        final long sessionExecTime = now - sessionStartTime;
        bufferedOutput.println(String.format("%d,%s,%d,%d", sessionId, "TOTAL", sessionExecTime, now));
        bufferedOutput.flush();
    }

    /**
     * Reads sessions [firstSession, firstSession + sessionsNumber) from the
     * file. Indexing starts from 0.
     */
    private static List<List<String>> readSessionsCommands(final String fileName, final int firstSession,
            final int sessionsNumber) {
        final List<String> cmds = SwiftSocialMain.readInputFromFile(fileName);
        final List<List<String>> sessionsCmds = new ArrayList<List<String>>();

        List<String> sessionCmds = new ArrayList<String>();
        for (int currentSession = 0, currentCmd = 0; currentSession < firstSession + sessionsNumber
                && currentCmd < cmds.size(); currentCmd++) {
            final String cmd = cmds.get(currentCmd);
            if (currentSession >= firstSession) {
                sessionCmds.add(cmd);
            }

            final String[] toks = cmd.split(";");
            final Commands cmdType = Commands.valueOf(toks[0].toUpperCase());
            if (cmdType == Commands.LOGOUT) {
                if (currentSession >= firstSession && currentSession < firstSession + sessionsNumber) {
                    sessionsCmds.add(sessionCmds);
                }
                sessionCmds = new ArrayList<String>();
                currentSession++;
            }
        }
        return sessionsCmds;
    }
}