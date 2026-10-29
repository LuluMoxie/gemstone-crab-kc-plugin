package com.gemstonecrabkc;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Talks to the gemstonecrabkc.com API. All calls are asynchronous on OkHttp's
 * thread pool; callbacks run on that pool, so callers must hop back to the
 * client thread themselves before touching game state.
 */
@Singleton
class SyncClient
{
	private static final Logger log = LoggerFactory.getLogger(SyncClient.class);
	private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
	private static final String SYNC_PATH = "api/plugin/sync";

	private final OkHttpClient http;
	private final Gson gson;

	@Inject
	SyncClient(OkHttpClient http, Gson gson)
	{
		this.http = http;
		this.gson = gson;
	}

	static HttpUrl baseUrl(String configured)
	{
		String value = configured == null || configured.trim().isEmpty()
			? GemstoneCrabKcConfig.DEFAULT_SERVER_URL
			: configured.trim();
		return HttpUrl.parse(value);
	}

	void sync(String serverUrl, String syncKey, SyncRequest body, Consumer<SyncResult> callback)
	{
		HttpUrl base = baseUrl(serverUrl);
		if (base == null)
		{
			callback.accept(SyncResult.error("The server URL in the plugin settings isn't valid."));
			return;
		}

		Request request = new Request.Builder()
			.url(base.newBuilder().addPathSegments(SYNC_PATH).build())
			.header("Authorization", "Bearer " + syncKey.trim())
			.post(RequestBody.create(JSON, gson.toJson(body)))
			.build();

		http.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.debug("Gemstone Crab KC sync failed", e);
				callback.accept(SyncResult.error("Couldn't reach gemstonecrabkc.com. Will try again after your next kill."));
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (ResponseBody responseBody = response.body())
				{
					SyncResult result = null;
					if (responseBody != null)
					{
						try
						{
							result = gson.fromJson(responseBody.charStream(), SyncResult.class);
						}
						catch (JsonParseException e)
						{
							log.debug("Unexpected sync response", e);
						}
					}
					if (result == null)
					{
						result = SyncResult.error("The leaderboard sent an unexpected reply (HTTP " + response.code() + ").");
					}
					result.setHttpStatus(response.code());
					if (!response.isSuccessful())
					{
						result.setOk(false);
					}
					callback.accept(result);
				}
			}
		});
	}
}
