package edu.duke.raft;

import java.util.Timer;
import java.util.TimerTask;
public class CandidateMode extends RaftMode {
  Timer myTimer;
  Timer myPollTimer;
  public void go () {
    synchronized (mLock) {

      mConfig.setCurrentTerm(mConfig.getCurrentTerm()+1, mID);
      int term = mConfig.getCurrentTerm();
      System.out.println ("S" +
        mID +
        "." +
        term +
        ": switched to candidate mode.");
      int numServers = mConfig.getNumServers();
      RaftResponses.setTerm(term);
      for(int i=1; i<=numServers; i++){ // sending requests for voting
        remoteRequestVote(i, term, mID, mLog.getLastIndex(), mLog.getLastTerm()); //start election; request votes from all servers
      }
      int range = (ELECTION_TIMEOUT_MAX - ELECTION_TIMEOUT_MIN) + 1;
      long randomNum = (long)(Math.random() * range) + ELECTION_TIMEOUT_MIN;
      myTimer = scheduleTimer(randomNum, 1);
      myPollTimer = scheduleTimer((long) (ELECTION_TIMEOUT_MIN/10.0), 2);
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
      // ": candidate got requestVote from " + candidateID);
      if (mID == candidateID){
        return 0;
      } else{
        if (candidateTerm > electTerm){ // if this candidate has higher term
          if (lastLogTerm > logTerm || (lastLogTerm == logTerm && lastLogIndex >= index)){
            // vote and step down if candidate has more/equal up-to-date log
            mConfig.setCurrentTerm(candidateTerm, candidateID);
            myTimer.cancel();
            myPollTimer.cancel();
            RaftServerImpl.setMode(new FollowerMode());
            return 0; //
          } else{ //if candidate has worse log, step down but do not vote
            mConfig.setCurrentTerm(candidateTerm, 0);
            myTimer.cancel();
            myPollTimer.cancel();
            RaftServerImpl.setMode(new FollowerMode());
            return electTerm;
          }
        } else{
          mConfig.setCurrentTerm(electTerm, 0);
          return electTerm;
        }
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
      if(term <= leaderTerm){//if my term is out of date or the same as leader, copy term set to follower
        mConfig.setCurrentTerm(leaderTerm, 0);
        myTimer.cancel();
        myPollTimer.cancel();
        RaftServerImpl.setMode(new FollowerMode());
        return 0;
      }
      return term;
    }
  }

  // @param id of the timer that timed out
  public void handleTimeout (int timerID) {
    synchronized (mLock) {
      int term = mConfig.getCurrentTerm ();
      if (timerID == 1){ // if it's the election timer
        myTimer.cancel();
        myPollTimer.cancel();
        // System.out.println ("S" +
        //   mID +
        //   "." +
        //   term +
        //   ": candidate timeout");
        go();
      } else{ // if it's the polling timer
        // System.out.println ("S" +
        //   mID +
        //   "." +
        //   term +
        //   ": candidate start polling");

        int[] voteResult = RaftResponses.getVotes(term); //check if array is null
        if (voteResult == null){
          assert false;
        } else {
          double countZero = 0;
          for (int i = 1; i < voteResult.length; i++){

            // System.out.println ("S" +
            //   mID +
            //   "." +
            //   term +
            //   ": vote result: " + voteResult[i]);

            if (voteResult[i] == 0){
              countZero++;
            }
          }
          double ratio = countZero/((double) (voteResult.length - 1.0));
          // System.out.println ("S" +
          //   mID +
          //   "." +
          //   term +
          //   ": voteResult " + countZero + "length: " + (voteResult.length - 1) + "ratio: " + ratio);
          if (countZero/((double) (voteResult.length - 1.0)) > 0.5){
            myPollTimer.cancel();
            myTimer.cancel();
            RaftServerImpl.setMode(new LeaderMode());
          } else {
            myPollTimer.cancel();
            myPollTimer = scheduleTimer((long) (ELECTION_TIMEOUT_MIN/10.0), 2);
          }
        }
      }
    }
  }
}
