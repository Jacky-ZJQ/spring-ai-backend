package com.jacky.ai.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jacky.ai.entity.po.School;
import com.jacky.ai.mapper.SchoolMapper;
import com.jacky.ai.service.ISchoolService;
import org.springframework.stereotype.Service;

/**
 * 校区表 服务实现类
 */
@Service
public class SchoolServiceImpl extends ServiceImpl<SchoolMapper, School> implements ISchoolService {

}
