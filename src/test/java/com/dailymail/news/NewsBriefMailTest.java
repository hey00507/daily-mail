package com.dailymail.news;

import com.dailymail.config.MailConfig;
import com.dailymail.core.ClaudeService;
import com.dailymail.core.MailContent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NewsBriefMailTest {

    @Mock
    RssService rssService;

    @Mock
    ClaudeService claudeService;

    @Mock
    MailConfig mailConfig;

    @InjectMocks
    NewsBriefMail newsBriefMail;

    @Test
    void name은_news_brief() {
        assertThat(newsBriefMail.name()).isEqualTo("news-brief");
    }

    @Test
    void isEnabled_설정이_true면_활성화() {
        var moduleConfig = new MailConfig.ModuleConfig(true, List.of("정치", "경제", "IT"), null, false);
        when(mailConfig.modules()).thenReturn(Map.of("news-brief", moduleConfig));

        assertThat(newsBriefMail.isEnabled()).isTrue();
    }

    @Test
    void isEnabled_설정이_없으면_비활성화() {
        when(mailConfig.modules()).thenReturn(Map.of());

        assertThat(newsBriefMail.isEnabled()).isFalse();
    }

    @Test
    void isEnabled_설정이_false면_비활성화() {
        var moduleConfig = new MailConfig.ModuleConfig(false, null, null, false);
        when(mailConfig.modules()).thenReturn(Map.of("news-brief", moduleConfig));

        assertThat(newsBriefMail.isEnabled()).isFalse();
    }

    @Test
    void generate_정상_콘텐츠_생성() {
        var politicsItem = new RssService.NewsItem("정치 기사", "https://pol.com", "정치 내용", "조선일보");
        var econItem = new RssService.NewsItem("경제 기사", "https://econ.com", "경제 내용", "한국경제");
        var itItem = new RssService.NewsItem("IT 기사", "https://it.com", "IT 내용", "중앙일보");

        when(rssService.fetchByCategory("정치", 5)).thenReturn(List.of(politicsItem));
        when(rssService.fetchByCategory("경제", 5)).thenReturn(List.of(econItem));
        when(rssService.fetchByCategory("IT", 5)).thenReturn(List.of(itItem));
        when(claudeService.ask(anyString())).thenReturn("한 줄 요약");

        MailContent content = newsBriefMail.generate();

        assertThat(content).isNotNull();
        assertThat(content.subject()).contains("[News]");
        assertThat(content.template()).isEqualTo("news-brief");
        assertThat(content.variables()).containsKey("newsMap");

        @SuppressWarnings("unchecked")
        Map<String, List<NewsBriefMail.SummarizedNews>> newsMap =
                (Map<String, List<NewsBriefMail.SummarizedNews>>) content.variables().get("newsMap");
        assertThat(newsMap).containsKeys("정치", "경제", "IT");

        verify(claudeService, times(3)).ask(anyString());
    }

    @Test
    void generate_뉴스가_없어도_빈_리스트로_처리() {
        when(rssService.fetchByCategory(anyString(), eq(5))).thenReturn(List.of());

        MailContent content = newsBriefMail.generate();

        assertThat(content).isNotNull();
        assertThat(content.subject()).contains("0선");
        verify(claudeService, never()).ask(anyString());
    }

    @Test
    void generate_요약_실패해도_빈_요약으로_계속_진행() {
        var item = new RssService.NewsItem("기사", "https://link.com", "내용", "출처");
        when(rssService.fetchByCategory("정치", 5)).thenReturn(List.of(item));
        when(rssService.fetchByCategory("경제", 5)).thenReturn(List.of());
        when(rssService.fetchByCategory("IT", 5)).thenReturn(List.of());
        when(claudeService.ask(anyString())).thenThrow(new RuntimeException("API 에러"));

        MailContent content = newsBriefMail.generate();

        assertThat(content).isNotNull();
        @SuppressWarnings("unchecked")
        Map<String, List<NewsBriefMail.SummarizedNews>> newsMap =
                (Map<String, List<NewsBriefMail.SummarizedNews>>) content.variables().get("newsMap");
        assertThat(newsMap.get("정치")).hasSize(1);
        assertThat(newsMap.get("정치").getFirst().summary()).isEmpty();
    }
}
