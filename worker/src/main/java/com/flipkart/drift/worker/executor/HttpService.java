package com.flipkart.drift.worker.executor;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.*;

import java.util.Map;

public interface HttpService {
    @GET
    Call<ResponseBody> get(@Url String url, @HeaderMap Map<String, String> headers, @QueryMap Map<String, String> urlParams);

    @POST
    Call<ResponseBody> post(@Url String url, @HeaderMap Map<String, String> headers, @QueryMap Map<String, String> urlParams, @Body Map<String, Object> body);
    //todo fix below
    @POST
    @FormUrlEncoded
    Call<ResponseBody> post(@Url String url, @HeaderMap Map<String, String> headers, @Field("grant_type") String grantType);

    @PUT
    Call<ResponseBody> put(@Url String url, @HeaderMap Map<String, String> headers, @QueryMap Map<String, String> urlParams, @Body Map<String, Object> body);

    @DELETE
    Call<ResponseBody> delete(@Url String url, @HeaderMap Map<String, String> headers, @QueryMap Map<String, String> urlParams);
}

