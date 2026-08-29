package com.onlinejudge.crs.config;

import com.onlinejudge.integration.config.AbstractDemoDataSeeder;
import com.onlinejudge.integration.config.DemoDataContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import static com.onlinejudge.integration.config.DemoDataContext.*;

@Component
public class CrsDemoDataSeeder extends AbstractDemoDataSeeder {
    public CrsDemoDataSeeder(JdbcTemplate jdbc) { super(jdbc); }

    @Override
    public void seed(DemoDataContext c) {
        if (!ready("crs_course", "crs_course_member", "crs_chapter", "crs_resource", "crs_announcement")) return;
        insert(COURSE, "crs_course", """
                INSERT INTO crs_course (id,course_name,description,teacher_id,semester,category,cover_url,enrollment_mode,
                    invite_code,max_students,start_date,end_date,status,is_deleted,created_at,updated_at)
                VALUES (?,'数据结构全流程演示课','覆盖登录、课程、学习、实验、作业、成绩、通知的验收演示课程。',?,
                    '2025-2026-2','计算机基础','/assets/back.jpg','INVITE','INT95',80,?,?,'ACTIVE',FALSE,?,?)
                """, COURSE, c.teacherId(), c.courseStart(), c.courseEnd(), c.publishedAt(), c.publishedAt());
        jdbc.update("UPDATE crs_course SET start_date=?,end_date=?,status='ACTIVE',updated_at=? WHERE id=? AND is_deleted=FALSE",
                c.courseStart(), c.courseEnd(), c.publishedAt(), COURSE);
        insertByCount("SELECT COUNT(*) FROM crs_course_member WHERE course_id=? AND user_id=?",
                new Object[]{COURSE,c.teacherId()}, """
                INSERT INTO crs_course_member (course_id,user_id,role,join_method,join_status,approved_by,joined_at,last_access_at,is_deleted,created_at,updated_at)
                VALUES (?,?,'TEACHER','CREATED','ACTIVE',?,TIMESTAMP '2026-06-01 08:00:00',TIMESTAMP '2026-06-08 09:00:00',FALSE,
                    TIMESTAMP '2026-06-01 08:00:00',TIMESTAMP '2026-06-08 09:00:00')
                """, COURSE,c.teacherId(),c.teacherId());
        insertByCount("SELECT COUNT(*) FROM crs_course_member WHERE course_id=? AND user_id=?",
                new Object[]{COURSE,c.studentId()}, """
                INSERT INTO crs_course_member (course_id,user_id,role,join_method,join_status,approved_by,joined_at,last_access_at,is_deleted,created_at,updated_at)
                VALUES (?,?,'STUDENT','INVITE_CODE','ACTIVE',?,TIMESTAMP '2026-06-01 08:05:00',TIMESTAMP '2026-06-08 09:10:00',FALSE,
                    TIMESTAMP '2026-06-01 08:05:00',TIMESTAMP '2026-06-08 09:10:00')
                """, COURSE,c.studentId(),c.teacherId());
        insert(CHAPTER,"crs_chapter","""
                INSERT INTO crs_chapter (id,course_id,parent_id,chapter_name,sort_order,objective,visible_status,chapter_type,is_deleted,created_at,updated_at)
                VALUES (?,?,NULL,'第 1 章 线性表与自动评测',1,'完成课程资源学习后提交实验和作业，并查看成绩通知。',1,1,FALSE,
                    TIMESTAMP '2026-06-01 08:10:00',TIMESTAMP '2026-06-01 08:10:00')
                """,CHAPTER,COURSE);
        insert(RESOURCE,"crs_resource","""
                INSERT INTO crs_resource (id,course_id,chapter_id,resource_name,resource_type,visibility,publish_at,storage_key,
                    original_filename,content_type,file_size,upload_user_id,is_deleted,created_at,updated_at)
                VALUES (?,?,?,'线性表实验讲义.pdf','DOCUMENT','STUDENT',TIMESTAMP '2026-06-01 08:15:00','demo/int95/linear-list-guide.pdf',
                    '线性表实验讲义.pdf','application/pdf',204800,?,FALSE,TIMESTAMP '2026-06-01 08:15:00',TIMESTAMP '2026-06-01 08:15:00')
                """,RESOURCE,COURSE,CHAPTER,c.teacherId());
        insertByCount("SELECT COUNT(*) FROM crs_announcement WHERE course_id=? AND title=?",
                new Object[]{COURSE,"全流程验收演示安排"}, """
                INSERT INTO crs_announcement (course_id,title,content,is_top,publisher_id,is_deleted,created_at,updated_at)
                VALUES (?,'全流程验收演示安排','请按资源学习、实验提交、作业提交、成绩查看和通知中心的顺序完成演示。',TRUE,?,FALSE,
                    TIMESTAMP '2026-06-01 08:20:00',TIMESTAMP '2026-06-01 08:20:00')
                """,COURSE,c.teacherId());
    }
}
