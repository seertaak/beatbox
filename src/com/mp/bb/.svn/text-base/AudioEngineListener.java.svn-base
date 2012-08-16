package com.mp.bb;

import com.mp.bb.BeatAnalyzer.AnalyzeInfo;

public interface AudioEngineListener {
	
	/**
	 * This method gets called while the <code>AudioEngine</code> is in
	 * countin mode. 
	 * @param remBeats - the number of beats remaining until recording starts.
	 */
	void onClick(int remBeats); 
	
	/**
	 * This method gets called when recording begins.
	 */
	void onRecord();
	
	/**
	 * This method gets called after recording ends, and analyze process
	 * begins.
	 */
	void onAnalyze();
	
	/**
	 * This method gets called after analyze process ends, and refinement
	 * process can begin.
	 */
	void onRefine();

	void doneAnalyzing(AnalyzeInfo result);

}
