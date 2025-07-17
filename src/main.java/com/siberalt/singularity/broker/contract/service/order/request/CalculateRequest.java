package com.siberalt.singularity.broker.contract.service.order.request;

public class CalculateRequest {
    private PostOrderRequest postOrderRequest;

    public CalculateRequest(PostOrderRequest postOrderRequest) {
        this.postOrderRequest = postOrderRequest;
    }

    public PostOrderRequest getPostOrderRequest() {
        return postOrderRequest;
    }

    public CalculateRequest setPostOrderRequest(PostOrderRequest postOrderRequest) {
        this.postOrderRequest = postOrderRequest;
        return this;
    }

    public static CalculateRequest of(PostOrderRequest postOrderRequest) {
        return new CalculateRequest(postOrderRequest);
    }
}
