package com.onlinejudge.lrn.config;

import com.onlinejudge.integration.config.AbstractDemoDataSeeder;
import com.onlinejudge.integration.config.DemoDataContext;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

import static com.onlinejudge.integration.config.DemoDataContext.*;

@Component
@Order(30)
public class LearningGradeDemoDataSeeder extends AbstractDemoDataSeeder {
    public LearningGradeDemoDataSeeder(JdbcTemplate jdbc) { super(jdbc); }

    @Override
    public void seed(DemoDataContext c) {
        if (!ready("lrn_learning_progress", "lrn_learning_record", "lrn_learning_task",
                "lrn_notification", "t_grade_item", "t_grade_record")) return;
        seedLearning(c);
        seedTasks(c);
        seedGrades(c);
        seedNotifications(c.studentId());
    }

    private void seedLearning(DemoDataContext c) {
        insert(PROGRESS_RESOURCE, "lrn_learning_progress", """
                INSERT INTO lrn_learning_progress (id,user_id,course_id,chapter_id,source_module,source_id,progress_percent,last_position,status,updated_at)
                VALUES (?,?,?,?,'CRS',?,100,'page=12','COMPLETED',TIMESTAMP '2026-06-08 09:12:00')
                """, PROGRESS_RESOURCE,c.studentId(),COURSE,CHAPTER,RESOURCE);
        insert(RECORD_RESOURCE, "lrn_learning_record", """
                INSERT INTO lrn_learning_record (id,user_id,course_id,source_module,source_id,action_type,duration,started_at,ended_at,created_at)
                VALUES (?,?,?,'CRS',?,'ACCESS',1800,TIMESTAMP '2026-06-08 08:40:00',TIMESTAMP '2026-06-08 09:10:00',TIMESTAMP '2026-06-08 09:10:00')
                """, RECORD_RESOURCE,c.studentId(),COURSE,RESOURCE);
        LocalDateTime endedAt=LocalDateTime.now().withNano(0), startedAt=endedAt.minusMinutes(30), since=endedAt.toLocalDate().minusDays(6).atStartOfDay();
        jdbc.update("""
                INSERT INTO lrn_learning_record (user_id,course_id,source_module,source_id,action_type,duration,started_at,ended_at,created_at)
                SELECT ?,?,'CRS',?,'ACCESS',1800,?,?,? FROM (SELECT 1 marker) seed
                LEFT JOIN lrn_learning_record existing ON existing.user_id=? AND existing.course_id=? AND existing.source_module='CRS'
                  AND existing.source_id=? AND existing.action_type='ACCESS' AND existing.started_at>=?
                WHERE existing.id IS NULL
                """,c.studentId(),COURSE,RESOURCE,startedAt,endedAt,endedAt,c.studentId(),COURSE,RESOURCE,since);
        insertByCount("SELECT COUNT(*) FROM lrn_notification_setting WHERE user_id=?",new Object[]{c.studentId()},"""
                INSERT INTO lrn_notification_setting (user_id,enable_experiment,enable_homework,enable_grade,enable_announcement,enable_non_critical_reminder,created_at,updated_at)
                VALUES (?,TRUE,TRUE,TRUE,TRUE,TRUE,TIMESTAMP '2026-06-01 08:30:00',TIMESTAMP '2026-06-01 08:30:00')
                """,c.studentId());
    }

    private void seedTasks(DemoDataContext c) {
        insert(TASK_LAB,"lrn_learning_task","""
                INSERT INTO lrn_learning_task (id,user_id,course_id,source_module,source_id,task_type,title,deadline,progress,status,action_url,snapshot_at,created_at,updated_at)
                VALUES (?,?,?,'LAB',?,'EXPERIMENT','实验一：线性表操作',TIMESTAMP '2026-06-30 23:59:59',100,'COMPLETED','/courses/9501/labs/950201?role=student',TIMESTAMP '2026-06-08 09:20:00',TIMESTAMP '2026-06-01 09:00:00',TIMESTAMP '2026-06-08 09:20:00')
                """,TASK_LAB,c.studentId(),COURSE,LAB);
        insert(TASK_HOMEWORK,"lrn_learning_task","""
                INSERT INTO lrn_learning_task (id,user_id,course_id,source_module,source_id,task_type,title,deadline,progress,status,action_url,snapshot_at,created_at,updated_at)
                VALUES (?,?,?,'HWK',?,'HOMEWORK','作业一：线性表复杂度分析',TIMESTAMP '2026-06-30 23:59:59',100,'COMPLETED','/courses/9501/homeworks/950301?role=student',TIMESTAMP '2026-06-08 09:30:00',TIMESTAMP '2026-06-01 09:30:00',TIMESTAMP '2026-06-08 09:30:00')
                """,TASK_HOMEWORK,c.studentId(),COURSE,HOMEWORK);
        insert(TASK_OPEN_LAB,"lrn_learning_task","""
                INSERT INTO lrn_learning_task (id,user_id,course_id,source_module,source_id,task_type,title,deadline,progress,status,action_url,snapshot_at,created_at,updated_at)
                VALUES (?,?,?,'LAB',?,'EXPERIMENT','回归实验：线性表边界操作',?,0,'IN_PROGRESS','/courses/9501/labs/950211?role=student',?,?,?)
                """,TASK_OPEN_LAB,c.studentId(),COURSE,OPEN_LAB,c.deadline(),c.publishedAt(),c.publishedAt(),c.publishedAt());
        insert(TASK_OPEN_HOMEWORK,"lrn_learning_task","""
                INSERT INTO lrn_learning_task (id,user_id,course_id,source_module,source_id,task_type,title,deadline,progress,status,action_url,snapshot_at,created_at,updated_at)
                VALUES (?,?,?,'HWK',?,'HOMEWORK','回归作业：复杂度说明',?,0,'IN_PROGRESS','/courses/9501/homeworks/950311?role=student',?,?,?)
                """,TASK_OPEN_HOMEWORK,c.studentId(),COURSE,OPEN_HOMEWORK,c.deadline(),c.publishedAt(),c.publishedAt(),c.publishedAt());
        jdbc.update("UPDATE lrn_learning_task SET deadline=?,progress=0,status='IN_PROGRESS',snapshot_at=?,updated_at=? WHERE id IN (?,?)",
                c.deadline(),c.publishedAt(),c.publishedAt(),TASK_OPEN_LAB,TASK_OPEN_HOMEWORK);
    }

    private void seedGrades(DemoDataContext c) {
        insert(GRADE_LAB,"t_grade_item","""
                INSERT INTO t_grade_item (id,course_id,name,source_type,source_id,full_score,weight,included_in_final,enabled,sort_order,created_by,deleted,created_at,updated_at)
                VALUES (?,?,'实验一成绩','LAB',?,100.00,0.4000,TRUE,TRUE,1,?,FALSE,TIMESTAMP '2026-06-08 09:32:00',TIMESTAMP '2026-06-08 09:32:00')
                """,GRADE_LAB,COURSE,LAB,c.teacherId());
        insert(GRADE_HOMEWORK,"t_grade_item","""
                INSERT INTO t_grade_item (id,course_id,name,source_type,source_id,full_score,weight,included_in_final,enabled,sort_order,created_by,deleted,created_at,updated_at)
                VALUES (?,?,'作业一成绩','HWK',?,100.00,0.6000,TRUE,TRUE,2,?,FALSE,TIMESTAMP '2026-06-08 09:32:00',TIMESTAMP '2026-06-08 09:32:00')
                """,GRADE_HOMEWORK,COURSE,HOMEWORK,c.teacherId());
        insert(GRADE_BATCH,"t_grade_calculation_batch","""
                INSERT INTO t_grade_calculation_batch (id,course_id,trigger_type,affected_item_count,affected_student_count,status,message,calculated_by,calculated_at)
                VALUES (?,?,'MANUAL_SYNC',2,1,'SUCCESS','INT-06 演示成绩同步完成',?,TIMESTAMP '2026-06-08 09:34:00')
                """,GRADE_BATCH,COURSE,c.teacherId());
        insert(RECORD_LAB,"t_grade_record","""
                INSERT INTO t_grade_record (id,course_id,student_id,grade_item_id,source_type,source_id,raw_score,weighted_score,grade_status,publish_status,comment,source_updated_at,calculated_at,published_at,created_at,updated_at)
                VALUES (?,?,?,?,'LAB',?,92.00,36.80,'SCORED','PUBLISHED','实验成绩来自自动评测',TIMESTAMP '2026-06-08 09:20:00',TIMESTAMP '2026-06-08 09:34:00',TIMESTAMP '2026-06-08 09:35:00',TIMESTAMP '2026-06-08 09:34:00',TIMESTAMP '2026-06-08 09:35:00')
                """,RECORD_LAB,COURSE,c.studentId(),GRADE_LAB,LAB);
        insert(RECORD_HOMEWORK,"t_grade_record","""
                INSERT INTO t_grade_record (id,course_id,student_id,grade_item_id,source_type,source_id,raw_score,weighted_score,grade_status,publish_status,comment,source_updated_at,calculated_at,published_at,created_at,updated_at)
                VALUES (?,?,?,?,'HWK',?,88.00,52.80,'SCORED','PUBLISHED','作业成绩来自教师批阅',TIMESTAMP '2026-06-08 09:28:00',TIMESTAMP '2026-06-08 09:34:00',TIMESTAMP '2026-06-08 09:35:00',TIMESTAMP '2026-06-08 09:34:00',TIMESTAMP '2026-06-08 09:35:00')
                """,RECORD_HOMEWORK,COURSE,c.studentId(),GRADE_HOMEWORK,HOMEWORK);
        insert(GRADE_SUMMARY,"t_course_grade_summary","""
                INSERT INTO t_course_grade_summary (id,course_id,student_id,final_score,final_status,publish_status,calculation_batch_id,published_at,created_at,updated_at)
                VALUES (?,?,?,89.60,'CALCULATED','PUBLISHED',?,TIMESTAMP '2026-06-08 09:35:00',TIMESTAMP '2026-06-08 09:34:00',TIMESTAMP '2026-06-08 09:35:00')
                """,GRADE_SUMMARY,COURSE,c.studentId(),GRADE_BATCH);
        insert(GRADE_PUBLISH,"t_grade_publish_record","""
                INSERT INTO t_grade_publish_record (id,course_id,idempotency_key,publish_scope,published_count,published_by,published_at,notification_status,remark)
                VALUES (?,?,'int95-demo-grade-publish','PARTIAL_STUDENTS',1,?,TIMESTAMP '2026-06-08 09:35:00','SENT','INT-06 演示成绩发布')
                """,GRADE_PUBLISH,COURSE,c.teacherId());
    }

    private void seedNotifications(long userId) {
        notification(950501,userId,"int95-lab-published","TASK","实验已发布","实验一：线性表操作已发布，请按时提交。","LAB",LAB,"/courses/9501/labs/950201?role=student",2);
        notification(950502,userId,"int95-lab-score","GRADE","实验成绩已发布","实验一成绩已发布，可查看自动评测结果和教师反馈。","LAB",LAB,"/courses/9501/labs/950201/submissions?role=student",3);
        notification(950503,userId,"int95-homework-published","TASK","作业已发布","作业一：线性表复杂度分析已发布。","HWK",HOMEWORK,"/courses/9501/homeworks/950301?role=student",2);
        notification(950504,userId,"int95-course-grade","GRADE","课程成绩已发布","数据结构全流程演示课成绩已发布，请查看成绩明细。","GRD",GRADE_SUMMARY,"/courses/9501?page=grades&role=student",3);
    }

    private void notification(long id,long userId,String key,String type,String title,String content,String module,long sourceId,String url,int priority) {
        insertByCount("SELECT COUNT(*) FROM lrn_notification WHERE user_id=? AND idempotency_key=?",new Object[]{userId,key},"""
                INSERT INTO lrn_notification (id,user_id,course_id,idempotency_key,title,content,type,priority,is_read,source_module,source_id,action_url,created_at,read_at,deleted_at)
                VALUES (?,?,?,?,?,?,?,?,FALSE,?,?,?,TIMESTAMP '2026-06-08 09:36:00',NULL,NULL)
                """,id,userId,COURSE,key,title,content,type,priority,module,sourceId,url);
        insertByCount("SELECT COUNT(*) FROM lrn_notification_status_log WHERE notification_id=? AND operation_type='CREATE'",new Object[]{id},"""
                INSERT INTO lrn_notification_status_log (notification_id,user_id,old_status,new_status,operation_type,operated_at)
                VALUES (?,?,NULL,'UNREAD','CREATE',TIMESTAMP '2026-06-08 09:36:00')
                """,id,userId);
    }
}
