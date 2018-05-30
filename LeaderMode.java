package edu.duke.raft;
import java.util.Timer;
import java.util.TimerTask;

public class LeaderMode extends RaftMode {
  // Timer myHeartbeatTimer;
  Timer appendTimer;
  public void go () {
    synchronized (mLock) {
      int term = mConfig.getCurrentTerm ();
      System.out.println ("S" +
			  mID +
			  "." +
			  term +
			  ": switched to leader mode.");
    // TimerTask task = new TimerTask() {
    //   public void sendHeartBeat (){
    //     int numServers = mConfig.getNumServers();
    //     for (int i = 1; i <= numServers; i++){
    //       remoteAppendEntries(i, term, mID, mLog.getLastIndex(), mLog.getLastTerm(), new Entry[]{}, mCommitIndex);
    //     }
    //   }
    // };
    // myHeartbeatTimer.schedule(task, 0, HEARTBEAT_INTERVAL)
    sendEntries();
    appendTimer = scheduleTimer(HEARTBEAT_INTERVAL, 1);
  }
  }

  // @param candidate’s term
  // @param candidate requesting vote
  // @param index of candidate’s last log entry
  // @param term of candidate’s last log entry
  // @return 0, if server votes for candidate; otherwise, server's
  // current term
  public int requestVote (int candidateTerm,
			  int candidateID,
			  int lastLogIndex,
			  int lastLogTerm) {
    synchronized (mLock) {
      int electTerm = mConfig.getCurrentTerm ();
      int logTerm = mLog.getLastTerm();
      int index = mLog.getLastIndex();
      if (candidateTerm > electTerm){ // if candidate has higher term than me
        if (lastLogTerm > logTerm || (lastLogTerm == logTerm && lastLogIndex >= index)){
          // vote and step down if candidate has more/equal up-to-date log
          mConfig.setCurrentTerm(candidateTerm, candidateID);
          appendTimer.cancel();
          RaftServerImpl.setMode(new FollowerMode());
          return 0; //
        } else{ // if candidate has worse log, step down but do not vote
          mConfig.setCurrentTerm(candidateTerm, 0);
          appendTimer.cancel();
          RaftServerImpl.setMode(new FollowerMode());
          return electTerm;
        }
      } else{ // don't vote and don't step down if candidate term is not greater
        mConfig.setCurrentTerm(electTerm, 0);

        return electTerm;
      }
  }
}

  // @param leader’s term
  // @param current leader
  // @param index of log entry before entries to append
  // @param term of log entry before entries to append
  // @param entries to append (in order of 0 to append.length-1)
  // @param index of highest committed entry
  // @return 0, if server appended entries; otherwise, server's
  // current term
  public int appendEntries (int leaderTerm,
			    int leaderID,
			    int prevLogIndex,
			    int prevLogTerm,
			    Entry[] entries,
			    int leaderCommit) {
    synchronized (mLock) {
      int term = mConfig.getCurrentTerm ();
      if(term < leaderTerm){//if my term is out of date, copy everything then set to follower
        mConfig.setCurrentTerm(leaderTerm, 0);
        appendTimer.cancel();
        RaftServerImpl.setMode(new FollowerMode());
        return 0;
      }
      return term;
    }
  }
private void sendEntries(){
  synchronized(mLock){
    int term = mConfig.getCurrentTerm ();
    int lastIndex = mLog.getLastIndex();
    Entry[] entries = new Entry[lastIndex+1];//making an array of entries
    RaftResponses.setTerm(term);
    for(int i=0; i<=lastIndex; i++){
      entries[i] = mLog.getEntry(i);
    }
    int numServers = mConfig.getNumServers();
    for (int i = 1; i <= numServers; i++){
      // if server has been repaired, send empty entry as heart beat
      // else add entries to heartbeat
      // System.out.print("S" +
      //   mID +
      //   "." +
      //   term +
      //   ": Leader sending logs to follower " + i + "; logs are- ");
      //   for(int j=0; j<entries.length; j++){
      //     System.out.print(entries[j] + " ");
      //   }
      //   System.out.println();
      remoteAppendEntries(i, term, mID, mLog.getLastIndex(), mLog.getLastTerm(), entries, mCommitIndex);
      // if a server has been repaired by this call, set repaired[i] = true
    }
  }
}
  // @param id of the timer that timed out
  public void handleTimeout (int timerID) {
    // synchronized (mLock) {
    //
    // }
    appendTimer.cancel();
    appendTimer = scheduleTimer(HEARTBEAT_INTERVAL, 1);
    sendEntries();

  }
}
