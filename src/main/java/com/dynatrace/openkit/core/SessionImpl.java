/***************************************************
 * (c) 2016-2017 Dynatrace LLC
 *
 * @author: Christian Schwarzbauer
 */
package com.dynatrace.openkit.core;

import com.dynatrace.openkit.api.Action;
import com.dynatrace.openkit.api.Session;
import com.dynatrace.openkit.core.configuration.AbstractConfiguration;
import com.dynatrace.openkit.protocol.Beacon;
import com.dynatrace.openkit.protocol.StatusResponse;
import com.dynatrace.openkit.providers.TimeProvider;

/**
 * Actual implementation of the {@link Session} interface.
 */
public class SessionImpl implements Session {

	// end time of this Session
	private long endTime = -1;

	// AbstractConfiguration and Beacon reference
	private AbstractConfiguration configuration;
	private Beacon beacon;

	// used for taking care to really leave all Actions at the end of this Session
	private SynchronizedQueue<Action> openRootActions = new SynchronizedQueue<Action>();

	// *** constructors ***

	public SessionImpl(AbstractConfiguration configuration, String clientIPAddress) {
		this.configuration = configuration;

		// beacon has to be created immediately, as the session start time is taken at beacon construction
		beacon = new Beacon(configuration, clientIPAddress);
		configuration.startSession(this);
	}

	// *** Session interface methods ***

	@Override
	public Action enterAction(String actionName) {
		return new ActionImpl(beacon, actionName, openRootActions);
	}

	@Override
	public void reportCrash(String errorName, String reason, String stacktrace) {
		beacon.reportCrash(errorName, reason, stacktrace);
	}

	@Override
	public void end() {
		// check if end() was already called before by looking at endTime
		if (endTime != -1) {
			return;
		}

		// leave all Root-Actions for sanity reasons
		while (!openRootActions.isEmpty()) {
			Action action = openRootActions.get();
			action.leaveAction();
		}

		endTime = TimeProvider.getTimestamp();

		// create end session data on beacon
		beacon.endSession(this);

		// finish session on configuration and stop managing it
		configuration.finishSession(this);
	}

	// *** public methods ***

	// sends the current Beacon state
	public StatusResponse sendBeacon() {
		return beacon.send();
	}

	// *** getter methods ***

	public long getEndTime() {
		return endTime;
	}

}
