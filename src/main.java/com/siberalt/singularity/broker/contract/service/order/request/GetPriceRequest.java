package com.siberalt.singularity.broker.contract.service.order.request;

public class GetPriceRequest {
    private PostOrderRequest postOrderRequest;

    public GetPriceRequest(PostOrderRequest postOrderRequest) {
        this.postOrderRequest = postOrderRequest;
    }

    public PostOrderRequest getPostOrderRequest() {
        return postOrderRequest;
    }

    public GetPriceRequest setPostOrderRequest(PostOrderRequest postOrderRequest) {
        this.postOrderRequest = postOrderRequest;
        return this;
    }

    public static GetPriceRequest of(PostOrderRequest postOrderRequest) {
        return new GetPriceRequest(postOrderRequest);
    }
}
