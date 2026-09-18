package com.zzkingcc.stringer.runtime.tool;

import com.zzkingcc.stringer.api.tool.ToolDescriptor;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.service.tool.ToolExecutor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 域的存活：工具断开（熔断 / 判死摘副本）不该让域跟着消失，入口也不该把它判成不存在。
 */
class ToolProfileRetentionTest {

    private final ToolRegistry registry = new ToolRegistry();

    private static ToolRegistry.Registered tool(String name, String source, List<String> profiles) {
        ToolDescriptor d = new ToolDescriptor(name, "desc", "cat", "1",
                null, true, true, List.of(), profiles, null, source);
        ToolSpecification spec = ToolSpecification.builder().name(name).description("desc").build();
        ToolExecutor noop = (request, context) -> "ok";
        return new ToolRegistry.Registered(d, spec, noop, List.of());
    }

    /** 域一经声明就不再抹掉：副本全部摘除后，它仍在已知域里、入口仍受理 */
    @Test
    void knownProfiles_surviveReplicaRemoval() {
        registry.register(tool("local_tool", "stringer", List.of("ops")));
        registry.replaceInstanceTools("i1", "http://10.0.0.5:8081/invoke",
                List.of(tool("remote_tool", "remote://i1", List.of("finance"))));

        assertTrue(registry.acceptsProfile("finance"));

        registry.removeInstance("i1");
        assertTrue(registry.find("remote_tool").isEmpty(), "副本应已摘除");
        assertTrue(registry.knownProfiles().contains("finance"), "域不应随工具断开而消失");
        assertTrue(registry.acceptsProfile("finance"), "域还可用，不能判 10004");
    }

    /** 见过域之后才开始拦：陌生域仍要被拒，这条 fail-fast 不能松 */
    @Test
    void unknownProfileStillRejectedOnceAnyDeclared() {
        registry.register(tool("local_tool", "stringer", List.of("ops")));
        assertFalse(registry.acceptsProfile("ghost"));
    }
}
