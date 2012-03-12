package swift.client.proto;

import swift.clocks.CausalityClock;
import swift.clocks.Timestamp;

/**
 * TODO: complete, adapt to RPC library (error handling etc.), document.
 * 
 * @author mzawirski
 */
public interface SwiftServer {
    FetchObjectDeltaReply fetchObjectDelta(FetchObjectDeltaRequest request);

    CRDTState fetchObjectVersion(FetchObjectVersionRequest request);

    Timestamp generateTimestamp(GenerateTimestampRequest request);

    KeepaliveRequest keepalive(KeepaliveRequest request);

    void unsubscribeNotifications(UnsubscribeNotificationsRequest request);

    BlockingCommitReply blockingTranslateAndCommit(BlockingCommitRequest request);

    SubmitUpdatesReply submitUpdates(SubmitUpdatesRequest request);

    CausalityClock getLatestClock();
}