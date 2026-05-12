package com.fancy.taxiagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fancy.taxiagent.domain.entity.UserPOI;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserPOIMapper extends BaseMapper<UserPOI> {
}
