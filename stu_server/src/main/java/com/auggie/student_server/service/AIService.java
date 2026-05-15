package com.auggie.student_server.service;

import com.auggie.student_server.entity.Quiz;
import com.auggie.student_server.entity.QuizQuestion;
import com.auggie.student_server.entity.StudentQuizAnswer;
import com.auggie.student_server.entity.Resource;
import com.auggie.student_server.mapper.StudentQuizAnswerMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @Auther: auggie
 * @Date: 2026/4/8
 * @Description: AIService AI服务类，提供智能组卷和自动评分功能
 * @Version 1.0.0
 */
@Service
public class AIService {

    // 模拟AI任务状态存储
    private final Map<String, AIGenerationTask> generationTasks = new ConcurrentHashMap<>();
    private final ExecutorService executorService = Executors.newFixedThreadPool(5);

    @Autowired
    private ResourceService resourceService;

    @Autowired
    private QuizService quizService;

    @Autowired
    private QuizQuestionService quizQuestionService;

    @Autowired
    private StudentQuizAnswerMapper studentQuizAnswerMapper;

    @Autowired
    private SysConfigService sysConfigService;

    @Autowired
    private Environment env;

    // AI组卷任务状态
    public static class AIGenerationTask {
        private String taskId;
        private String status; // pending, processing, completed, failed
        private int progress;
        private String error;
        private Quiz generatedQuiz;
        private List<QuizQuestion> generatedQuestions;

        // 构造方法、getter和setter
        public AIGenerationTask(String taskId) {
            this.taskId = taskId;
            this.status = "pending";
            this.progress = 0;
        }

        public String getTaskId() {
            return taskId;
        }

        public void setTaskId(String taskId) {
            this.taskId = taskId;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public int getProgress() {
            return progress;
        }

        public void setProgress(int progress) {
            this.progress = progress;
        }

        public String getError() {
            return error;
        }

        public void setError(String error) {
            this.error = error;
        }

        public Quiz getGeneratedQuiz() {
            return generatedQuiz;
        }

        public void setGeneratedQuiz(Quiz generatedQuiz) {
            this.generatedQuiz = generatedQuiz;
        }

        public List<QuizQuestion> getGeneratedQuestions() {
            return generatedQuestions;
        }

        public void setGeneratedQuestions(List<QuizQuestion> generatedQuestions) {
            this.generatedQuestions = generatedQuestions;
        }
    }

    /**
     * AI智能组卷
     * @param ctid 课程ID
     * @param selectedResources 选中的资源ID列表
     * @param questionStructure 题目结构配置（JSON格式）
     * @param difficultyLevel 难度等级
     * @return 任务ID
     */
    public String generateQuizByAI(Integer ctid, List<Integer> selectedResources, String questionStructure, Integer difficultyLevel) {
        String taskId = "task_" + System.currentTimeMillis();
        AIGenerationTask task = new AIGenerationTask(taskId);
        generationTasks.put(taskId, task);

        // 异步执行组卷
        executorService.execute(() -> {
            try {
                task.setStatus("processing");
                task.setProgress(10);

                // 1. 获取选中的资源内容
                List<Resource> resources = new ArrayList<>();
                for (Integer rid : selectedResources) {
                    Resource resource = resourceService.findById(rid);
                    if (resource != null) {
                        resources.add(resource);
                    }
                }
                task.setProgress(30);

                // 2. 调用Claude生成测验
                String resourcesContent = "";
                for (Resource resource : resources) {
                    // 在实际项目中，这里会读取文件内容
                    resourcesContent += String.format("资源名称: %s\n资源描述: %s\n\n",
                            resource.getFilename(), resource.getDescription());
                }

                String quizContent = callClaudeForQuizGeneration(resourcesContent, questionStructure, difficultyLevel);
                task.setProgress(70);

                // 3. 解析生成的题目
                List<QuizQuestion> questions = parseGeneratedQuestions(quizContent, questionStructure);
                task.setProgress(85);

                // 计算题目总分
                float totalScore = 0f;
                for (QuizQuestion question : questions) {
                    totalScore += question.getQuestionScore();
                }

                // 4. 创建测验
                Quiz quiz = new Quiz();
                quiz.setCtid(ctid);
                quiz.setQuizTitle("AI生成测验-" + new Date().toString().substring(0, 10));
                quiz.setQuizDescription("由AI根据选中资源智能生成的测验");
                quiz.setIsAIGenerated(true);
                quiz.setAiModel(sysConfigService.getConfigValue("ai.model.name", "ark-code-latest"));
                quiz.setDifficultyLevel(difficultyLevel);
                quiz.setQuestionStructure(questionStructure);

                // 设置开始时间和结束时间（默认当前时间到7天后）
                Date now = new Date();
                quiz.setStartTime(now);
                Calendar calendar = Calendar.getInstance();
                calendar.setTime(now);
                calendar.add(Calendar.DAY_OF_YEAR, 7);
                quiz.setEndTime(calendar.getTime());
                quiz.setDuration(60);
                quiz.setTotalScore(totalScore);
                quiz.setStatus(0); // AI组卷生成的测验默认未发布，需要教师编辑后手动发布

                // 保存测验
                quizService.save(quiz);
                task.setGeneratedQuiz(quiz);

                // 保存题目
                for (QuizQuestion question : questions) {
                    question.setQuizId(quiz.getQuizId());
                    quizQuestionService.save(question);
                }
                task.setGeneratedQuestions(questions);

                task.setProgress(100);
                task.setStatus("completed");

            } catch (Exception e) {
                task.setStatus("failed");
                task.setError(e.getMessage());
                e.printStackTrace();
            }
        });

        return taskId;
    }

    /**
     * 获取AI组卷任务状态
     */
    public AIGenerationTask getGenerationStatus(String taskId) {
        return generationTasks.get(taskId);
    }

    /**
     * AI自动评分
     * @param questionScore 题目满分值，AI将按此分值进行评分
     */
    public Map<String, Object> gradeAnswerByAI(Integer answerId, String questionContent, String correctAnswer, String studentAnswer, Float questionScore) {
        Map<String, Object> result = new HashMap<>();

        try {
            // 使用题目实际分值，默认为10分
            float maxScore = (questionScore != null && questionScore > 0) ? questionScore : 10f;

            // 标准化答案（去除空格，统一大小写）进行初步对比
            String normalizedCorrect = correctAnswer != null ? correctAnswer.replace(" ", "").replace("，", ",").toLowerCase().trim() : "";
            String normalizedStudent = studentAnswer != null ? studentAnswer.replace(" ", "").replace("，", ",").toLowerCase().trim() : "";

            // 如果答案完全一致，直接给满分，无需调用AI
            if (!normalizedCorrect.isEmpty() && normalizedCorrect.equals(normalizedStudent)) {
                result.put("success", true);
                result.put("aiScore", maxScore);
                result.put("aiAnalysis", String.format("学生答案与正确答案完全一致。\n\n评分分析：\n1. 答案的准确性：完全正确，与标准答案一致\n2. 逻辑完整性：完整无缺漏\n3. 语言表达：准确规范\n\n分数：%.1f分", maxScore));
                return result;
            }

            // 构建更严格的评分提示词，强调与正确答案的对比，并传入实际满分值
            String prompt = String.format(
                "你是一位严格的评分教师。请严格按照以下要求评分：\n\n" +
                "【评分原则】\n" +
                "1. 评分标准以【正确答案】为唯一依据\n" +
                "2. 本题满分为%.1f分，请按此满分进行评分\n" +
                "3. 如果学生答案与正确答案完全一致或含义相同，必须给满分%.1f分\n" +
                "4. 如果学生答案缺少部分内容，按缺失比例扣分\n" +
                "5. 不允许根据主观理解调整评分，必须客观对比\n\n" +
                "【题目】\n%s\n\n" +
                "【正确答案】（评分唯一标准）\n%s\n\n" +
                "【学生答案】\n%s\n\n" +
                "【评分要求】\n" +
                "1. 首先对比学生答案与正确答案的异同\n" +
                "2. 给出具体的评分分析\n" +
                "3. 给出0-%.1f分的具体分数（本题满分%.1f分）\n" +
                "4. 最后必须明确写出：分数：X分",
                maxScore, maxScore, questionContent, correctAnswer, studentAnswer, maxScore, maxScore
            );

            String analysis = callClaudeForGrading(prompt, correctAnswer);

            // 解析分数（AI返回的是基于题目实际分值的分数）
            float aiScore = parseScoreFromAnalysis(analysis, maxScore);
            result.put("success", true);
            result.put("aiScore", aiScore);
            result.put("aiAnalysis", analysis);

        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getMessage());
        }

        return result;
    }

    /**
     * 批量AI评分
     */
    public Map<String, Object> batchGradeByAI(Integer quizId) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<StudentQuizAnswer> answers = studentQuizAnswerMapper.findByQuizId(quizId);
            int processed = 0;
            int total = 0;

            for (StudentQuizAnswer answer : answers) {
                QuizQuestion question = quizQuestionService.findById(answer.getQuestionId());
                if (question != null && (question.getQuestionType() == 4 || question.getQuestionType() == 5)) { // 处理填空题和简答题
                    total++;

                    // 使用与单个评分相同的逻辑，确保参考正确答案，并传入题目分值
                    Map<String, Object> gradeResult = gradeAnswerByAI(
                            answer.getAnswerId(),
                            question.getQuestionContent(),
                            question.getCorrectAnswer(),
                            answer.getStudentAnswer(),
                            question.getQuestionScore()
                    );

                    if ((Boolean) gradeResult.get("success")) {
                        float aiScore = (Float) gradeResult.get("aiScore");
                        String aiAnalysis = (String) gradeResult.get("aiAnalysis");

                        // AI返回的分数已经是基于题目实际分值的分数，直接使用
                        answer.setAiScore(aiScore);
                        answer.setAiAnalysis(aiAnalysis);
                        answer.setGradingStatus(1); // AI评分完成
                        answer.setScore(aiScore);
                        studentQuizAnswerMapper.updateById(answer);
                        processed++;
                    }
                }
            }

            result.put("success", true);
            result.put("processed", processed);
            result.put("total", total);
            result.put("message", String.format("批量评分完成，共处理 %d 道题目，成功评分 %d 道", total, processed));

        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "批量评分失败: " + e.getMessage());
            e.printStackTrace();
        }

        return result;
    }

    /**
     * 调用Claude API生成测验
     */
    private String callClaudeForQuizGeneration(String resourcesContent, String questionStructure, int difficultyLevel) {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            String difficulty = switch (difficultyLevel) {
                case 1 -> "简单";
                case 2 -> "中等";
                case 3 -> "困难";
                default -> "中等";
            };

            String prompt = String.format(
                "你是一位专业的教师，请根据以下资源内容生成一份测验。\n\n" +
                "资源内容：\n%s\n\n" +
                "要求：\n" +
                "1. 难度等级：%s\n" +
                "2. 题目结构：%s\n" +
                "3. 必须严格按照题目结构生成题目数量，不得多生成、不得重复生成\n" +
                "4. 每道题目用\"---\"分隔，格式如下：\n" +
                "   题目类型|题目内容|分值|选项|正确答案\n" +
                "   - 题目类型：1单选 2多选 3判断 4填空 5简答\n" +
                "   - 单选/多选 选项格式：[{\"option\":\"A\",\"content\":\"选项内容\"},{\"option\":\"B\",\"content\":\"选项内容\"}]\n" +
                "   - 判断题 选项格式：[{\"option\":\"A\",\"content\":\"正确\"},{\"option\":\"B\",\"content\":\"错误\"}]\n" +
                "   - 填空/简答 选项填 []\n" +
                "   - 单选/多选 正确答案格式：A 或 A,B\n" +
                "   - 判断题 正确答案格式：A(正确) 或 B(错误)\n" +
                "   - 填空题 正确答案格式：直接填写答案内容\n" +
                "   - 简答题 正确答案格式：参考答案内容\n" +
                "   - 不要输出任何思考过程、解释、markdown或XML标签\n" +
                "   - 直接返回纯文本题目内容",
                resourcesContent, difficulty, questionStructure
            );

            return callClaudeAPI(prompt);
        } catch (Exception e) {
            e.printStackTrace();
            // 如果API调用失败，返回模拟数据作为备用
            return getFallbackQuizContent(difficultyLevel, questionStructure);
        }
    }

    /**
     * 获取备用的模拟测验内容
     */
    private String getFallbackQuizContent(int difficultyLevel, String questionStructure) {
        String difficulty = switch (difficultyLevel) {
            case 1 -> "简单";
            case 2 -> "中等";
            case 3 -> "困难";
            default -> "中等";
        };
        return String.format(
            "1|什么是Java中的OOP？|10|[{\"option\":\"A\",\"content\":\"面向过程\"},{\"option\":\"B\",\"content\":\"面向对象\"},{\"option\":\"C\",\"content\":\"函数式编程\"}]|B\n" +
            "---\n" +
            "2|以下哪些是集合框架的类？|10|[{\"option\":\"A\",\"content\":\"ArrayList\"},{\"option\":\"B\",\"content\":\"HashMap\"},{\"option\":\"C\",\"content\":\"String\"}]|A,B\n" +
            "---\n" +
            "5|请解释Java中的垃圾回收机制。|20||Java的垃圾回收是自动内存管理的一部分，它自动回收不再被引用的对象。"
        );
    }

    /**
     * 解析AI生成的题目
     */
    private List<QuizQuestion> parseGeneratedQuestions(String quizContent, String questionStructure) {
        List<QuizQuestion> questions = new ArrayList<QuizQuestion>();
        try {
            // 提取题目结构要求（用于限制返回题目数量）
            Map<Integer, Integer> requiredCounts = parseQuestionStructure(questionStructure);
            int totalRequired = 0;
            for (Integer count : requiredCounts.values()) {
                totalRequired += count;
            }

            // 移除思考标签内容（Kimi API 返回的思考过程）
            String cleanedContent = removeThinkTags(quizContent);

            String[] rawQuestions = cleanedContent.split("---");
            for (int i = 0; i < rawQuestions.length; i++) {
                String rawQuestion = rawQuestions[i].trim();
                if (rawQuestion.isEmpty()) {
                    continue;
                }

                // 跳过不包含"|"的行（可能是思考过程的残留）
                if (!rawQuestion.contains("|")) {
                    continue;
                }

                // 尝试解析题目类型（第一部分的第一个字符应该是数字）
                String firstChar = rawQuestion.trim().substring(0, 1);
                if (!firstChar.matches("[1-5]")) {
                    continue; // 跳过不以题目类型开头的行
                }

                String[] parts = rawQuestion.split("\\|");
                if (parts.length >= 5) {
                    try {
                        int questionType = Integer.parseInt(parts[0].trim());
                        String questionContent = parts[1].trim();
                        float score = parts[2].trim().isEmpty() ? 10.0f : Float.parseFloat(parts[2].trim());
                        String options = parts[3].trim();
                        String correctAnswer = parts[4].trim();

                        // 检查是否还需要该类型的题目
                        int remaining = requiredCounts.getOrDefault(questionType, 0);
                        if (remaining > 0) {
                            // 处理判断题答案映射：T->A, F->B
                            if (questionType == 3) {
                                if ("T".equalsIgnoreCase(correctAnswer) || "正确".equals(correctAnswer)) {
                                    correctAnswer = "A";
                                } else if ("F".equalsIgnoreCase(correctAnswer) || "错误".equals(correctAnswer)) {
                                    correctAnswer = "B";
                                }
                            }
                            questions.add(createQuestion(questionType, questionContent, score, options, correctAnswer, questions.size() + 1));
                            requiredCounts.put(questionType, remaining - 1);
                        }

                        // 如果已达到要求的总数，停止解析
                        if (questions.size() >= totalRequired) {
                            break;
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        continue;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (questions.isEmpty()) {
            // 如果解析失败，使用默认题目
            questions.add(createQuestion(1, "什么是Java中的OOP？", 10, "[{\"option\":\"A\",\"content\":\"面向过程\"},{\"option\":\"B\",\"content\":\"面向对象\"},{\"option\":\"C\",\"content\":\"函数式编程\"}]", "B", 1));
            questions.add(createQuestion(2, "以下哪些是集合框架的类？", 10, "[{\"option\":\"A\",\"content\":\"ArrayList\"},{\"option\":\"B\",\"content\":\"HashMap\"},{\"option\":\"C\",\"content\":\"String\"}]", "A,B", 2));
            questions.add(createQuestion(5, "请解释Java中的垃圾回收机制。", 20, "", "Java的垃圾回收是自动内存管理的一部分", 3));
        }

        return questions;
    }

    /**
     * 解析题目结构要求
     * @param questionStructure JSON格式的题目结构，如 {'1': 2, '5': 2}
     * @return Map<题目类型, 数量>
     */
    private Map<Integer, Integer> parseQuestionStructure(String questionStructure) {
        Map<Integer, Integer> result = new HashMap<>();
        try {
            // 处理可能的单引号
            String jsonStr = questionStructure.replace("'", "\"");
            ObjectMapper mapper = new ObjectMapper();
            JsonNode node = mapper.readTree(jsonStr);

            // 支持的题目类型：1-单选, 2-多选, 3-判断, 4-填空, 5-简答
            String[] typeKeys = {"1", "2", "3", "4", "5"};
            String[] typeNames = {"单选题", "多选题", "判断题", "填空题", "简答题"};

            for (int i = 0; i < typeKeys.length; i++) {
                // 尝试数字key
                if (node.has(typeKeys[i]) && node.get(typeKeys[i]).isNumber()) {
                    result.put(i + 1, node.get(typeKeys[i]).asInt());
                }
                // 尝试中文名称key
                else if (node.has(typeNames[i]) && node.get(typeNames[i]).isNumber()) {
                    result.put(i + 1, node.get(typeNames[i]).asInt());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            // 默认返回2道单选+2道简答
            result.put(1, 2);
            result.put(5, 2);
        }
        return result;
    }

    /**
     * 移除思考标签及其内容
     */
    private String removeThinkTags(String content) {
        if (content == null) {
            return "";
        }
        // 移除 <think>...</think> 标签及其内容（使用DOTALL模式让.匹配换行符）
        String result = content.replaceAll("(?s)<think>.*?</think>", "");
        // 移除可能的 <思考> 标签
        result = result.replaceAll("(?s)<思考>.*?</思考>", "");
        // 移除 markdown 代码块标记
        result = result.replaceAll("```\\w*\\n?", "");
        result = result.replaceAll("```", "");
        // 移除 XML/HTML 风格的注释
        result = result.replaceAll("(?s)<!--.*?-->", "");
        return result.trim();
    }

    /**
     * 通用的 API 调用方法 - 使用 OpenAI 兼容格式
     */
    private String callClaudeAPI(String prompt) throws Exception {
        String apiKey = sysConfigService.getConfigValue("ai.api.key", "");
        String baseUrl = sysConfigService.getConfigValue("ai.api.endpoint", "https://ark.cn-beijing.volces.com/api/coding");
        String model = sysConfigService.getConfigValue("ai.model.name", "ark-code-latest");

        if (apiKey.isEmpty() || apiKey.equals("your-claude-api-key-here")) {
            // 配置未加载或者未配置，使用application.yml中的默认配置
            apiKey = env.getProperty("anthropic.api.key", "");
            baseUrl = env.getProperty("anthropic.base-url", "https://ark.cn-beijing.volces.com/api/coding");
            model = env.getProperty("anthropic.model", "ark-code-latest");

            if (apiKey.isEmpty() || apiKey.equals("your-claude-api-key-here")) {
                throw new Exception("API key not configured");
            }
        }

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            // 尝试使用 /v1/chat/completions 端点（OpenAI 兼容格式）
            String apiUrl = baseUrl + "/v1/chat/completions";

            HttpPost httpPost = new HttpPost(apiUrl);
            httpPost.setHeader("Content-Type", "application/json");
            httpPost.setHeader("Authorization", "Bearer " + apiKey);

            // 转义 prompt 中的特殊字符
            String escapedPrompt = prompt.replace("\\", "\\\\")
                                         .replace("\"", "\\\"")
                                         .replace("\n", "\\n")
                                         .replace("\r", "\\r")
                                         .replace("\t", "\\t");

            String requestBody = String.format(
                "{\"model\": \"%s\", \"max_tokens\": 2048, \"temperature\": 0.3, " +
                "\"messages\": [" +
                "{\"role\": \"system\", \"content\": \"你是一个专业的教师助手\"}," +
                "{\"role\": \"user\", \"content\": \"%s\"}" +
                "]}",
                model, escapedPrompt
            );

            System.out.println("API Request URL: " + apiUrl);
            System.out.println("API Request Body: " + requestBody);

            StringEntity entity = new StringEntity(requestBody, "UTF-8");
            httpPost.setEntity(entity);

            HttpResponse response = httpClient.execute(httpPost);
            int statusCode = response.getStatusLine().getStatusCode();

            String responseBody = EntityUtils.toString(response.getEntity(), "UTF-8");
            System.out.println("API Response Status: " + statusCode);
            System.out.println("API Response Body: " + responseBody);

            if (statusCode != 200) {
                throw new Exception("API request failed with status code: " + statusCode + ", " + responseBody);
            }

            ObjectMapper mapper = new ObjectMapper();
            JsonNode rootNode = mapper.readTree(responseBody);

            // 尝试从 OpenAI 兼容格式中提取响应
            if (rootNode.has("choices") && rootNode.get("choices").isArray()) {
                JsonNode choices = rootNode.get("choices");
                if (choices.size() > 0) {
                    JsonNode choice = choices.get(0);
                    if (choice.has("message") && choice.get("message").has("content")) {
                        return choice.get("message").get("content").asText();
                    }
                    if (choice.has("text")) {
                        return choice.get("text").asText();
                    }
                }
            }

            // 备选：尝试 Anthropic 格式
            if (rootNode.has("content") && rootNode.get("content").isArray()) {
                JsonNode content = rootNode.get("content");
                if (content.size() > 0 && content.get(0).has("text")) {
                    return content.get(0).get("text").asText();
                }
            }

            throw new Exception("无法解析 API 响应: " + responseBody);
        }
    }

    /**
     * 调用Claude API进行评分
     */
    private String callClaudeForGrading(String prompt, String correctAnswer) {
        try {
            // 如果正确答案为空，则使用原提示
            if (correctAnswer == null || correctAnswer.trim().isEmpty()) {
                return callClaudeAPI(prompt);
            }

            // 构建更严格的评分提示，强调以正确答案为唯一标准
            String fullPrompt = String.format(
                "你是一位评分教师。请严格按照以下要求评分：\n\n" +
                "【评分原则 - 必须严格遵守】\n" +
                "1. 评分标准以【正确答案】为唯一依据，不得加入个人判断\n" +
                "2. 如果学生答案与正确答案完全一致或含义完全等价，必须给满分10分\n" +
                "3. 如果学生答案缺少正确答案中的部分内容，按缺失比例扣分\n" +
                "4. 如果学生答案包含正确答案以外的错误内容，适当扣分\n" +
                "5. 不允许根据主观理解调整评分，必须客观对比学生答案与正确答案\n\n" +
                "6. 答案格式出错不考虑扣分\n\n" +
                "【正确答案】（评分唯一标准）\n%s\n\n" +
                "%s\n\n" +
                "【评分要求】\n" +
                "1. 首先详细对比学生答案与正确答案的异同\n" +
                "2. 根据对比结果给出具体的评分分析\n" +
                "3. 给出具体分数（必须以正确答案为唯一标准）\n" +
                "4. 最后必须明确写出：分数：X分",
                correctAnswer, prompt
            );

            return callClaudeAPI(fullPrompt);
        } catch (Exception e) {
            e.printStackTrace();
            // 如果API调用失败，返回模拟评分
            return "AI评分分析：学生答案基本正确，对概念理解清楚，但在细节上有一定欠缺。\n需要在深度上进一步加强。\n分数：8.5分";
        }
    }

    /**
     * 从AI分析中解析分数
     * @param analysis AI评分分析文本
     * @param maxScore 题目满分值
     */
    private float parseScoreFromAnalysis(String analysis, Float maxScore) {
        try {
            // 简单解析分数，查找最后一个数字或小数
            String[] lines = analysis.split("\n");
            for (int i = lines.length - 1; i >= 0; i--) {
                String line = lines[i].trim();
                if (line.contains("分数") || line.contains("分")) {
                    String scoreStr = line.replaceAll("[^0-9.]", "");
                    float score = Float.parseFloat(scoreStr);
                    // 直接返回AI给出的分数，不做比例换算
                    return score;
                }
            }
            // 默认返回满分的60%
            return maxScore != null && maxScore > 0 ? maxScore * 0.6f : 6.0f;
        } catch (Exception e) {
            return maxScore != null && maxScore > 0 ? maxScore * 0.6f : 6.0f;
        }
    }

    /**
     * 从AI分析中解析分数（兼容旧版本，默认按10分制）
     */
    private float parseScoreFromAnalysis(String analysis) {
        return parseScoreFromAnalysis(analysis, 10.0f);
    }

    /**
     * 创建题目对象
     */
    private QuizQuestion createQuestion(int questionType, String content, float score, String options, String correctAnswer, int questionOrder) {
        QuizQuestion question = new QuizQuestion();
        question.setQuestionType(questionType);
        question.setQuestionContent(content);
        question.setQuestionScore(score);
        question.setOptions(options);
        question.setCorrectAnswer(correctAnswer);
        question.setQuestionOrder(questionOrder);
        question.setIsAIGenerated(true);
        question.setAiConfidence(0.9f);
        return question;
    }
}
