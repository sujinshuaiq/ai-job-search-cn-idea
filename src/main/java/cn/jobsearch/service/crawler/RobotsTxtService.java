package cn.jobsearch.service.crawler;

import cn.jobsearch.service.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Component
public class RobotsTxtService {
    private final BossHttpClient httpClient;

    public RobotsTxtService(BossHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public void assertAllowed(URI target) {
        try {
            URI robotsUri = new URI(target.getScheme(), target.getAuthority(), "/robots.txt", null, null);
            String robots = httpClient.get(robotsUri);
            Decision decision = evaluate(robots, target);
            if (!decision.allowed()) {
                throw new ApiException(HttpStatus.FORBIDDEN, "ROBOTS_DISALLOWED",
                        "BOSS 直聘 robots.txt 禁止自动访问该搜索地址（规则：" + decision.rule() + "）。未请求目标页面，请使用离线 HTML 导入或官方授权渠道");
            }
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "ROBOTS_UNAVAILABLE", "无法确认 robots.txt 权限，已按失败关闭策略停止采集");
        }
    }

    Decision evaluate(String robotsTxt, URI target) {
        List<Rule> wildcard = new ArrayList<>();
        List<Rule> specific = new ArrayList<>();
        List<Rule> current = null;
        boolean groupHasDirectives = false;
        for (String rawLine : robotsTxt.lines().toList()) {
            String line = rawLine.replaceFirst("#.*$", "").trim();
            if (line.isBlank() || !line.contains(":")) continue;
            String key = line.substring(0, line.indexOf(':')).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(line.indexOf(':') + 1).trim();
            if (key.equals("user-agent")) {
                if (groupHasDirectives) {
                    current = null;
                    groupHasDirectives = false;
                }
                String agent = value.toLowerCase(Locale.ROOT);
                current = agent.equals("*") ? wildcard : (agent.contains("jobsearchcn") ? specific : null);
            } else if (current != null && (key.equals("allow") || key.equals("disallow"))) {
                if (!value.isBlank()) current.add(new Rule(key.equals("allow"), value));
                groupHasDirectives = true;
            }
        }
        List<Rule> rules = specific.isEmpty() ? wildcard : specific;
        String pathQuery = target.getRawPath() + (target.getRawQuery() == null ? "" : "?" + target.getRawQuery());
        Rule best = null;
        for (Rule rule : rules) {
            if (matches(rule.pattern(), pathQuery) && (best == null || rule.weight() > best.weight()
                    || (rule.weight() == best.weight() && rule.allow()))) {
                best = rule;
            }
        }
        return best == null ? new Decision(true, "") : new Decision(best.allow(), best.pattern());
    }

    private boolean matches(String pattern, String pathQuery) {
        boolean exactEnd = pattern.endsWith("$");
        String value = exactEnd ? pattern.substring(0, pattern.length() - 1) : pattern;
        String[] pieces = value.split("\\*", -1);
        StringBuilder regex = new StringBuilder("^");
        for (int i = 0; i < pieces.length; i++) {
            if (i > 0) regex.append(".*");
            regex.append(Pattern.quote(pieces[i]));
        }
        if (exactEnd) regex.append("$");
        return Pattern.compile(regex.toString()).matcher(pathQuery).find();
    }

    record Decision(boolean allowed, String rule) { }
    private record Rule(boolean allow, String pattern) {
        int weight() { return pattern.replace("*", "").replace("$", "").length(); }
    }
}
