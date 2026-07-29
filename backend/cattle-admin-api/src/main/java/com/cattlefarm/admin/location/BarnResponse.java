package com.cattlefarm.admin.location;
public record BarnResponse(String barnId, String barnCode, String barnName, String barnType,
                           Integer capacity, String status, String remark, long cattleCount, int version) {}
