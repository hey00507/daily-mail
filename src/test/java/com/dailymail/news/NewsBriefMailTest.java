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
        var moduleConfig = new MailConfig.ModuleConfig(true, List.of("tech"), null, false);
        when(mailConfig.modules()).thenReturn(Map.of("news-brief", moduleConfig));

        assertThat(newsBriefMail.isEnabled()).isTrue();
    }

    @Test
    void isEnabled_설정이_없으면_비활성화() {
        when(mailConfig.modules()).thenReturn(Map.of());

        assertThat(newsBriefMail.isEnabled()).isFalse();
    }

    @Test
    void generate_정상_콘텐츠_생성() {
        var techItem = new RssService.NewsItem("Tech 기사", "https://tech.com", "tech 내용", "HN");
        var generalItem = new RssService.NewsItem("경제 기사", "https://econ.com", "경제 내용", "한경");

        when(rssService.fetchTech(5)).thenReturn(List.of(techItem));
        when(rssService.fetchGeneral(5)).thenReturn(List.of(generalItem));
        when(claudeService.ask(anyString())).thenReturn("한 줄 요약");

        MailContent content = newsBriefMail.generate();

        assertThat(content).isNotNull();
        assertThat(content.subject()).contains("[News]");
        assertThat(content.template()).isEqualTo("news-brief");
        assertThat(content.variables()).containsKey("techNews");
        assertThat(content.variables()).containsKey("generalNews");

        verify(claudeService, times(2)).ask(anyString());
    }

    @Test
    void generate_뉴스가_없어도_빈_리스트로_처리() {
        when(rssService.fetchTech(5)).thenReturn(List.of());
        when(rssService.fetchGeneral(5)).thenReturn(List.of());

        MailContent content = newsBriefMail.generate();

        assertThat(content).isNotNull();
        assertThat(content.subject()).contains("0선");
        verify(claudeService, never()).ask(anyString());
    }

    @Test
    void generate_요약_실패해도_빈_요약으로_계속_진행() {
        var item = new RssService.NewsItem("기사", "https://link.com", "내용", "출처");
        when(rssService.fetchTech(5)).thenReturn(List.of(item));
        when(rssService.fetchGeneral(5)).thenReturn(List.of());
        when(claudeService.ask(anyString())).thenThrow(new RuntimeException("API 에러"));

        MailContent content = newsBriefMail.generate();

        assertThat(content).isNotNull();
        @SuppressWarnings("unchecked")
        List<NewsBriefMail.SummarizedNews> techNews =
                (List<NewsBriefMail.SummarizedNews>) content.variables().get("techNews");
        assertThat(techNews).hasSize(1);
        assertThat(techNews.getFirst().summary()).isEmpty();
    }
}
