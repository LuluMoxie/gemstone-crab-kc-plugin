package com.gemstonecrabkc;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class GemstoneCrabKcPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(GemstoneCrabKcPlugin.class);
		RuneLite.main(args);
	}
}
