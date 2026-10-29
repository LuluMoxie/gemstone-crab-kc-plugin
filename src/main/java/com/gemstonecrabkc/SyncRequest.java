package com.gemstonecrabkc;

import java.util.Objects;

/**
 * What the plugin sends to the leaderboard. Only the logged-in player's own
 * data is included; nothing about other players is ever sent.
 */
final class SyncRequest
{
	/** RuneLite's per-account hash, sent as a string so it survives JSON number precision. */
	private final String accountHash;
	/** The player's display name (RSN). */
	private final String rsn;
	/** "main", "ironman" or "hardcore". */
	private final String accountType;
	private final int killCount;
	private final int top3Count;
	private final String pluginVersion;

	SyncRequest(String accountHash, String rsn, String accountType, int killCount, int top3Count, String pluginVersion)
	{
		this.accountHash = accountHash;
		this.rsn = rsn;
		this.accountType = accountType;
		this.killCount = killCount;
		this.top3Count = top3Count;
		this.pluginVersion = pluginVersion;
	}

	String getRsn()
	{
		return rsn;
	}

	int getKillCount()
	{
		return killCount;
	}

	@Override
	public boolean equals(Object o)
	{
		if (this == o)
		{
			return true;
		}
		if (!(o instanceof SyncRequest))
		{
			return false;
		}
		SyncRequest other = (SyncRequest) o;
		return killCount == other.killCount
			&& top3Count == other.top3Count
			&& Objects.equals(accountHash, other.accountHash)
			&& Objects.equals(rsn, other.rsn)
			&& Objects.equals(accountType, other.accountType)
			&& Objects.equals(pluginVersion, other.pluginVersion);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(accountHash, rsn, accountType, killCount, top3Count, pluginVersion);
	}
}
