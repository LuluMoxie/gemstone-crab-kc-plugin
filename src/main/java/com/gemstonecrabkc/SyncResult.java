package com.gemstonecrabkc;

/**
 * The leaderboard's reply to a sync. Fields are filled by Gson from the
 * server's JSON; {@code httpStatus} is set by {@link SyncClient}.
 */
final class SyncResult
{
	private boolean ok;
	/** Kill count the leaderboard now shows for this account. */
	private Integer killCount;
	/** Position on the "All Accounts" leaderboard, if ranked. */
	private Integer rank;
	/** Human-readable explanation, mainly for errors. */
	private String message;
	/** Short machine-readable error code, e.g. BAD_KEY or RSN_TAKEN. */
	private String code;

	private transient int httpStatus;

	static SyncResult error(String message)
	{
		SyncResult r = new SyncResult();
		r.ok = false;
		r.message = message;
		return r;
	}

	boolean isOk()
	{
		return ok;
	}

	void setOk(boolean ok)
	{
		this.ok = ok;
	}

	Integer getKillCount()
	{
		return killCount;
	}

	Integer getRank()
	{
		return rank;
	}

	String getMessage()
	{
		return message;
	}

	String getCode()
	{
		return code;
	}

	int getHttpStatus()
	{
		return httpStatus;
	}

	void setHttpStatus(int httpStatus)
	{
		this.httpStatus = httpStatus;
	}
}
