package com.cattlefarm.admin.cattle.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cattlefarm.admin.cattle.model.CattleEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CattleMapper extends BaseMapper<CattleEntity> {
}
