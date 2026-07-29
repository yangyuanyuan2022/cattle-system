package com.cattlefarm.admin.cattle;

import java.util.List;

public record CattlePageResponse(long page, long pageSize, long total, List<CattleResponse> items) {
}
