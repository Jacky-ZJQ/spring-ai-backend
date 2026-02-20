package com.jacky.ai.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jacky.ai.entity.po.Course;
import com.jacky.ai.mapper.CourseMapper;
import com.jacky.ai.service.ICourseService;
import org.springframework.stereotype.Service;

/**
 * 学科表 服务实现类
 */
@Service
public class CourseServiceImpl extends ServiceImpl<CourseMapper, Course> implements ICourseService {

}
