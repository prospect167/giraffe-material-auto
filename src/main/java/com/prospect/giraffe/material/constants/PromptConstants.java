package com.prospect.giraffe.material.constants;

/**
 * 图片去重（去水印）提示词常量类
 * 
 * 所有提示词集中管理，每次迭代保留历史版本，方便对比和回退
 * 
 * 支持的变量占位符：
 *   {removeTargets} - 要去除的内容，如"水印、logo、多余文字"
 *   {preserveTexts} - 要保留的文字，如"，保留「生命树」"（无保留文字时为空字符串）
 *
 * @author giraffe
 */
public class PromptConstants {

    private PromptConstants() {
        // 工具类禁止实例化
    }

    // ========================= 提示词版本定义 =========================

    /**
     * V8 - 强去除+构图约束版（当前使用）
     * 策略：把去除指令放在最强位置，构图保持作为附加约束
     * 对比 V7：V7 的"与原图完全一致"权重太高，模型连水印都不敢删
     * 对比 V6：V6 的"保持原图不变"太笼统，模型会偏移人物位置
     * 效果：水印彻底去除 + 人物位置构图不变
     */
    public static final String V8 = "将图片上的{removeTargets}全部清除干净{preserveTexts}，"
            + "清除区域用周围像素自然填充，人物位置和构图不变";

    /**
     * V7 - 构图锁定版
     * 策略：在 V6 基础上明确约束人物位置和构图布局，防止模型移动人物
     * 效果：构图保持较好，但"与原图完全一致"导致水印也被保留，去除不干净
     */
    public static final String V7 = "去除图片上的{removeTargets}{preserveTexts}，"
            + "保持人物位置、构图布局和画面比例与原图完全一致";

    /**
     * V6 - 简洁直接版
     * 策略：提示词尽量简短，配合 strength=0.50 让模型最小程度修改原图
     * 效果：水印去除干净，但人物位置会偏移
     */
    public static final String V6 = "去除图片上的{removeTargets}{preserveTexts}，保持原图不变";

    /**
     * V5 - 局部修复版
     * 策略：模拟 inpainting，强调只擦除叠加元素，用周围像素填充
     * 效果：提示词过长反而干扰模型，效果不如 V4
     */
    public static final String V5 = "这是一张影视剧宣传海报。请对这张图片执行局部修复："
            + "仅擦除图片上后期叠加的{removeTargets}。"
            + "擦除区域用相邻像素自然填充。"
            + "{preserveTexts}"
            + "除擦除区域外，图片所有像素保持原样不变，不修改任何人物、场景、色调。"
            + "输出图片必须与原图保持相同的分辨率和画质，人物面部必须清晰可辨。";

    /**
     * V4 - 面部保护版
     * 策略：强调保持人物面部细节，区分"去除"和"保留"的对象
     * 效果：水印去除较好，但面部仍有轻微失真
     */
    public static final String V4 = "严格保持这张图片中所有人物的面部五官、表情、发型、服装、姿态完全不变，"
            + "严格保持原图的构图、色调、光影、背景完全不变，"
            + "只需要擦除图片上后期叠加的{removeTargets}。"
            + "{preserveTexts}"
            + "输出图片必须与原图保持相同的分辨率和画质，人物面部必须清晰可辨。";

    /**
     * V3 - 详细描述版
     * 策略：详细列出要去除和保留的内容，指定处理方式
     * 效果：人物面部模糊严重，模型过度重绘
     */
    public static final String V3 = "请根据这张图片生成一张干净的版本，去除{removeTargets}，"
            + "但保持图片原始风格、构图和人物面部细节不变。"
            + "{preserveTexts}"
            + "输出高清图片，保持清晰度。";

    /**
     * V2 - 初始版（纯文生图，未传入原始图片）
     * 策略：简单描述去重需求
     * 效果：生成全新图片，与原图差异大
     */
    public static final String V2 = "请帮我去除这张图片上的{removeTargets}，"
            + "保留原始画面内容和构图不变。"
            + "{preserveTexts}";

    /**
     * V1 - 最初版本（仅供参考，当时未实现图生图）
     */
    public static final String V1 = "生成一张干净无水印的图片，去除所有{removeTargets}。{preserveTexts}";

    // ========================= 当前使用的提示词 =========================

    /**
     * 当前生效的提示词模板
     * 
     * 修改提示词时：
     * 1. 新增一个版本常量（如 V7），写入新的提示词
     * 2. 将 CURRENT_PROMPT 指向新版本
     * 3. 旧版本保留不动，方便对比和回退
     */
    public static final String CURRENT_PROMPT = V8;
}

