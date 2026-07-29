package com.cattlefarm.admin.location;
public record HerdResponse(String herdId, String herdCode, String herdName, String herdType,
                           String barnId, String barnName, String status, String remark, long cattleCount, int version) {}
