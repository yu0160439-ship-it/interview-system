package com.example.interview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.interview.entity.Staff;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface StaffMapper extends BaseMapper<Staff> {
}
