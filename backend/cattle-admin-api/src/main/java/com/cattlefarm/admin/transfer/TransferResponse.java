package com.cattlefarm.admin.transfer;
public record TransferResponse(String batchId,String transferId,String cattleId,String fromBarnId,String fromHerdId,
 String toBarnId,String toHerdId,boolean capacityExceeded,String warning,int cattleVersion) {}
