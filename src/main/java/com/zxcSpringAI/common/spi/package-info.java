/**
 * SPI 扩展点接口定义包
 *
 * <p>所有扩展接口均通过 Java SPI(Service Provider Interface)机制加载。
 * 扩展方在 META-INF/services/ 下注册实现类即可插拔,不需要改 Stringer 源码。</p>
 *
 * <p>扩展原则:
 * <ol>
 *   <li>约定大于配置 — 默认实现覆盖 80% 场景</li>
 *   <li>零侵入 — 接口只依赖 JDK,不强制引入 Spring / LangChain4j 等第三方库</li>
 *   <li>失败隔离 — 一个扩展实现抛异常不影响其他扩展和主流程</li>
 *   <li>可观测 — 每个扩展点执行前后可打 Trace 日志</li>
 * </ol>
 *
 * <p>当前为接口占位阶段,Phase 2 统一抽象后替换各层对 LangChain4j 接口的直接依赖。</p>
 *
 * @see ToolProvider          工具注册扩展
 * @see CheckpointSaver       检查点持久化扩展
 * @see ChatMemoryStore       会话记忆存储扩展
 * @see ContentRetriever      检索策略扩展
 * @see ToolExecutionInterceptor 工具调用拦截器扩展
 */
package com.zxcSpringAI.common.spi;
