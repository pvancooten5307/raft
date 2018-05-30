package edu.duke.raft;
import java.lang.Math;
import java.util.Timer;
import java.util.TimerTask;

public class FollowerMode extends RaftMode {
  Timer myTimer;
  long randomNum;
  public void go () {
    synchronized (mLock) {
      int term = mConfig.getCurrentTerm ();
      mConfig.setCurrentTerm(term, 0);
      System.out.println ("S" +
			  mID +
			  "." +
			  term +
			  ": switched to follower mode.");
      int range = (ELECTION_TIMEOUT_MAX - ELECTION_TIMEOUT_MIN) + 1;
      randomNum = (long)(Math.random() * range) + ELECTION_TIMEOUT_MIN;
      //System.out.println ("timer period: "+randomNum);
      if (mConfig.getTimeoutOverride() != -1) {
        randomNum = mConfig.getTimeoutOverride();
      }
      myTimer = scheduleTimer(randomNum, 1);
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
      // System.out.println ("S" +
      // mID +
      // "." +
      // electTerm +
      // ": follower got requestVote.");
      if (candidateTerm > electTerm) { //
        // check log; vote for the candidate, and update current term
        if (lastLogTerm > logTerm || (lastLogTerm == logTerm && lastLogIndex >= index)){
          // if candidate's log is more/equal up-to-date
          // System.out.println ("S" +
          // mID +
          // "." +
          // electTerm +
          // ": vote for Candidate " + candidateID + ": has larger term & more/equal up-to-date log.");
          myTimer.cancel();
          myTimer = scheduleTimer(randomNum, 1);
          mConfig.setCurrentTerm(candidateTerm, candidateID);
          return 0;
        } else{
          // System.out.println ("S" +
          // mID +
          // "." +
          // electTerm +
          // ": Candidate " + candidateID + " has larger term but older log.");
          mConfig.setCurrentTerm(candidateTerm, 0);
          return electTerm;
        }
      } else if (candidateTerm == electTerm){ // candidate has same term
        if (mConfig.getVotedFor() != 0){ // if I have voted, don't do anything
          // System.out.println ("S" +
          // mID +
          // "." +
          // electTerm +
          // ": no vote for Candidate " + candidateID + ": Same term, but I've already voted.");
          mConfig.setCurrentTerm(electTerm, 0);
          return electTerm;
        } else{ // If I haven't voted, check log and decide vote or not
          if (lastLogTerm > logTerm || (lastLogTerm == logTerm && lastLogIndex >= index)){
            // if candidate's log is more up-to-date
            // System.out.println ("S" +
            // mID +
            // "." +
            // electTerm +
            // ": vote for Candidate " + candidateID + ": Same term, candidate has better/equal log.");
            myTimer.cancel();
            myTimer = scheduleTimer(randomNum, 1);
            mConfig.setCurrentTerm(electTerm, candidateID);
            return 0;
          } else{
            // System.out.println ("S" +
            // mID +
            // "." +
            // electTerm +
            // ":did not vote for Candidate " + candidateID + ": Same term, candidate has worse log.");
            mConfig.setCurrentTerm(electTerm, 0);
            return electTerm;
          }
        }
      } else{
        // System.out.println ("S" +
        // mID +
        // "." +
        // electTerm +
        // ": no vote for Candidate " + candidateID + ": candidate has older term.");
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
      //if in same term, check the index to make sure the leader is more ahead of you

      if(term <= leaderTerm){//the leader is as or more up to date
        // System.out.println("S" +
        //   mID +
        //   "." +
        //   term +
        //   ": leader is as or more up to date. follower is hearing heartbeat.");
        myTimer.cancel();
        int range = (ELECTION_TIMEOUT_MAX - ELECTION_TIMEOUT_MIN) + 1;
        long randomNum = (long)(Math.random() * range) + ELECTION_TIMEOUT_MIN;
        //myTimer.cancel();
        myTimer = scheduleTimer(randomNum, 1);
        mConfig.setCurrentTerm(leaderTerm, 0);
        mLog.insert(entries, -1, -1);
        // System.out.print("S" +
        //   mID +
        //   "." +
        //   term +
        //   ": Follower updated logs: ");
        //   for(int i=0; i<entries.length; i++){
        //     System.out.print(entries[i] + " ");
        //   }
        //   System.out.println();
        return 0;
      }
      return term;
    }
  }

  // @param id of the timer that timed out
  public void handleTimeout (int timerID) {
    synchronized (mLock) {
      int term = mConfig.getCurrentTerm ();
      myTimer.cancel();

      // System.out.println ("S" +
      //   mID +
      //   "." +
      //   term +
      //   ": follower timeout");
      RaftServerImpl.setMode(new CandidateMode());
    }
  }
}
