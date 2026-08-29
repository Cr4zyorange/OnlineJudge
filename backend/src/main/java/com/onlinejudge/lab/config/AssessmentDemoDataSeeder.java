package com.onlinejudge.lab.config;

import com.onlinejudge.integration.config.AbstractDemoDataSeeder;
import com.onlinejudge.integration.config.DemoDataContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import static com.onlinejudge.integration.config.DemoDataContext.*;

@Component
public class AssessmentDemoDataSeeder extends AbstractDemoDataSeeder {
    public AssessmentDemoDataSeeder(JdbcTemplate jdbc) { super(jdbc); }

    @Override
    public void seed(DemoDataContext c) {
        if (!ready("lab_experiment","lab_testcase","lab_submission","lab_evaluation","lab_evaluation_result","lab_score",
                "t_hwk_homework","t_hwk_question","t_hwk_submission","t_hwk_evaluation","t_hwk_review_log")) return;
        seedLab(c); seedHomework(c);
    }

    private void seedLab(DemoDataContext c) {
        insert(LAB,"lab_experiment","""
                INSERT INTO lab_experiment (id,course_id,chapter_id,title,description,status,deadline,max_score,attachment_ids,
                    allowed_languages,evaluation_mode,auto_evaluate,report_required,time_limit_ms,memory_limit_kb,created_by,
                    published_at,deleted,created_at,updated_at)
                VALUES (?,?,?,'实验一：线性表操作','提交顺序表插入与删除程序，系统自动评测并发布成绩。','SCORE_PUBLISHED',
                    TIMESTAMP '2026-06-30 23:59:59',100,NULL,'python','DOCKER_IO',TRUE,FALSE,60000,262144,?,
                    TIMESTAMP '2026-06-01 09:00:00',FALSE,TIMESTAMP '2026-06-01 08:50:00',TIMESTAMP '2026-06-08 09:20:00')
                """,LAB,COURSE,CHAPTER,c.teacherId());
        insert(LAB_CASE,"lab_testcase","""
                INSERT INTO lab_testcase (id,lab_id,input,expected_output,score_weight,is_public,time_limit_ms,memory_limit_kb,order_num,deleted,created_at,updated_at)
                VALUES (?,?,'3\n1 2 3\n','1 2 3\n',100,TRUE,60000,262144,1,FALSE,TIMESTAMP '2026-06-01 09:05:00',TIMESTAMP '2026-06-01 09:05:00')
                """,LAB_CASE,LAB);
        insert(LAB_SUBMISSION,"lab_submission","""
                INSERT INTO lab_submission (id,lab_id,student_id,code_content,file_id,language,submit_status,evaluation_status,final_score,
                    auto_score,version,is_final,submitted_at,created_at,updated_at,deleted)
                VALUES (?,?,?,'print("1 2 3")',NULL,'python','SUBMITTED','ACCEPTED',92,92,1,TRUE,TIMESTAMP '2026-06-08 09:16:00',
                    TIMESTAMP '2026-06-08 09:16:00',TIMESTAMP '2026-06-08 09:18:00',FALSE)
                """,LAB_SUBMISSION,LAB,c.studentId());
        insert(LAB_EVALUATION,"lab_evaluation","""
                INSERT INTO lab_evaluation (id,submission_id,status,score,passed_cases,total_cases,time_used_ms,memory_used_kb,feedback,
                    compile_log,run_log,started_at,finished_at,created_at,updated_at)
                VALUES (?,?,'ACCEPTED',92,1,1,120,2048,'全部用例通过','','case#1 accepted',TIMESTAMP '2026-06-08 09:16:10',
                    TIMESTAMP '2026-06-08 09:16:20',TIMESTAMP '2026-06-08 09:16:10',TIMESTAMP '2026-06-08 09:16:20')
                """,LAB_EVALUATION,LAB_SUBMISSION);
        insert(LAB_RESULT,"lab_evaluation_result","""
                INSERT INTO lab_evaluation_result (id,submission_id,testcase_id,status,passed,score,actual_output,message,executed_at,created_at,updated_at)
                VALUES (?,?,?,'ACCEPTED',TRUE,92,'1 2 3\n','输出匹配',TIMESTAMP '2026-06-08 09:16:18',TIMESTAMP '2026-06-08 09:16:18',TIMESTAMP '2026-06-08 09:16:18')
                """,LAB_RESULT,LAB_SUBMISSION,LAB_CASE);
        insert(LAB_SCORE,"lab_score","""
                INSERT INTO lab_score (id,submission_id,report_id,teacher_id,auto_score,report_score,manual_score,final_score,comment,scored_at,updated_at)
                VALUES (?,?,NULL,?,92,NULL,NULL,92,'自动评测通过，成绩发布。',TIMESTAMP '2026-06-08 09:20:00',TIMESTAMP '2026-06-08 09:20:00')
                """,LAB_SCORE,LAB_SUBMISSION,c.teacherId());
        insert(OPEN_LAB,"lab_experiment","""
                INSERT INTO lab_experiment (id,course_id,chapter_id,title,description,status,deadline,max_score,attachment_ids,allowed_languages,
                    evaluation_mode,auto_evaluate,report_required,time_limit_ms,memory_limit_kb,created_by,published_at,deleted,created_at,updated_at)
                VALUES (?,?,?,'回归实验：线性表边界操作','开放期回归样例。提交可复现实验受理、自动评测、历史与教师处理流程。','PUBLISHED',?,100,NULL,
                    'python','DOCKER_IO',TRUE,FALSE,60000,262144,?,?,FALSE,?,?)
                """,OPEN_LAB,COURSE,CHAPTER,c.deadline(),c.teacherId(),c.publishedAt(),c.publishedAt(),c.publishedAt());
        insert(OPEN_LAB_CASE,"lab_testcase","""
                INSERT INTO lab_testcase (id,lab_id,input,expected_output,score_weight,is_public,time_limit_ms,memory_limit_kb,order_num,deleted,created_at,updated_at)
                VALUES (?,?,'0\n','EMPTY\n',100,TRUE,60000,262144,1,FALSE,?,?)
                """,OPEN_LAB_CASE,OPEN_LAB,c.publishedAt(),c.publishedAt());
        jdbc.update("UPDATE lab_experiment SET status='PUBLISHED',deadline=?,published_at=?,updated_at=? WHERE id=? AND deleted=FALSE",
                c.deadline(),c.publishedAt(),c.publishedAt(),OPEN_LAB);
    }

    private void seedHomework(DemoDataContext c) {
        insert(HOMEWORK,"t_hwk_homework","""
                INSERT INTO t_hwk_homework (id,course_id,chapter_id,title,description,type,status,total_score,deadline,allow_resubmit,
                    allow_late_submit,show_evaluation_before_publish,judge_config_id,created_by,published_at,is_deleted,created_at,updated_at)
                VALUES (?,?,?,'作业一：线性表复杂度分析','完成一次文本作业提交并由教师批阅发布成绩。','TEXT','SCORE_PUBLISHED',100.00,
                    TIMESTAMP '2026-06-30 23:59:59',TRUE,FALSE,TRUE,NULL,?,TIMESTAMP '2026-06-01 09:30:00',FALSE,
                    TIMESTAMP '2026-06-01 09:25:00',TIMESTAMP '2026-06-08 09:30:00')
                """,HOMEWORK,COURSE,CHAPTER,c.teacherId());
        insert(HOMEWORK_QUESTION,"t_hwk_question","""
                INSERT INTO t_hwk_question (id,homework_id,question_type,stem,options_json,answer_json,score,sort_order,created_at,updated_at)
                VALUES (?,?,'TEXT','说明顺序表插入操作的时间复杂度。',NULL,'{"reference":"平均 O(n)，尾插 O(1)。"}',100.00,1,
                    TIMESTAMP '2026-06-01 09:35:00',TIMESTAMP '2026-06-01 09:35:00')
                """,HOMEWORK_QUESTION,HOMEWORK);
        insert(HOMEWORK_SUBMISSION,"t_hwk_submission","""
                INSERT INTO t_hwk_submission (id,homework_id,student_id,submit_type,answer_text,answer_json,file_url,language,submit_status,
                    evaluation_status,review_status,auto_score,manual_score,final_score,comment,version,is_final,submitted_at,reviewed_by,
                    reviewed_at,created_at,updated_at,is_deleted)
                VALUES (?,?,?,'TEXT','顺序表插入平均需要移动 n/2 个元素，复杂度为 O(n)。',NULL,NULL,NULL,'SUBMITTED','NONE','REVIEWED',NULL,
                    88.00,88.00,'分析完整，边界情况可继续补充。',1,TRUE,TIMESTAMP '2026-06-08 09:24:00',?,TIMESTAMP '2026-06-08 09:28:00',
                    TIMESTAMP '2026-06-08 09:24:00',TIMESTAMP '2026-06-08 09:28:00',FALSE)
                """,HOMEWORK_SUBMISSION,HOMEWORK,c.studentId(),c.teacherId());
        insert(HOMEWORK_EVALUATION,"t_hwk_evaluation","""
                INSERT INTO t_hwk_evaluation (id,submission_id,homework_id,student_id,evaluation_type,status,score,passed_cases,total_cases,
                    time_used_ms,memory_used_kb,error_message,feedback,log_url,compile_log,run_log,reevaluation,triggered_by,started_at,finished_at,created_at,updated_at)
                VALUES (?,?,?,?,'OBJECTIVE_AUTO','ACCEPTED',88.00,1,1,NULL,NULL,NULL,'教师批阅完成',NULL,NULL,NULL,FALSE,?,
                    TIMESTAMP '2026-06-08 09:28:00',TIMESTAMP '2026-06-08 09:28:00',TIMESTAMP '2026-06-08 09:28:00',TIMESTAMP '2026-06-08 09:28:00')
                """,HOMEWORK_EVALUATION,HOMEWORK_SUBMISSION,HOMEWORK,c.studentId(),c.teacherId());
        insert(HOMEWORK_REVIEW,"t_hwk_review_log","""
                INSERT INTO t_hwk_review_log (id,submission_id,homework_id,student_id,operation_type,old_score,new_score,comment,operator_id,reason,created_at)
                VALUES (?,?,?,?,'REVIEW',NULL,88.00,'验收演示教师批阅记录',?,'INT-06 演示数据',TIMESTAMP '2026-06-08 09:28:00')
                """,HOMEWORK_REVIEW,HOMEWORK_SUBMISSION,HOMEWORK,c.studentId(),c.teacherId());
        insert(OPEN_HOMEWORK,"t_hwk_homework","""
                INSERT INTO t_hwk_homework (id,course_id,chapter_id,title,description,type,status,total_score,deadline,allow_resubmit,allow_late_submit,
                    show_evaluation_before_publish,judge_config_id,created_by,published_at,is_deleted,created_at,updated_at)
                VALUES (?,?,?,'回归作业：复杂度说明','开放期回归样例。用于复现文本提交、提交历史、教师批阅与反馈流程。','TEXT','PUBLISHED',100.00,
                    ?,TRUE,FALSE,TRUE,NULL,?,?,FALSE,?,?)
                """,OPEN_HOMEWORK,COURSE,CHAPTER,c.deadline(),c.teacherId(),c.publishedAt(),c.publishedAt(),c.publishedAt());
        insert(OPEN_HOMEWORK_QUESTION,"t_hwk_question","""
                INSERT INTO t_hwk_question (id,homework_id,question_type,stem,options_json,answer_json,score,sort_order,created_at,updated_at)
                VALUES (?,?,'TEXT','说明空顺序表插入首个元素的时间复杂度。',NULL,'{"reference":"O(1)"}',100.00,1,?,?)
                """,OPEN_HOMEWORK_QUESTION,OPEN_HOMEWORK,c.publishedAt(),c.publishedAt());
        jdbc.update("UPDATE t_hwk_homework SET status='PUBLISHED',deadline=?,published_at=?,updated_at=? WHERE id=? AND is_deleted=FALSE",
                c.deadline(),c.publishedAt(),c.publishedAt(),OPEN_HOMEWORK);
    }
}
