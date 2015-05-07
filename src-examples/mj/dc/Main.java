package mj.dc;

import swift.dc.DCServer;

public class Main {
    public static void main(String[] args) throws InterruptedException {
        DCServer.main(new String[] { "-servers", "localhost", "-integrated" });
        Thread.sleep(1000*1000);
    }
}
