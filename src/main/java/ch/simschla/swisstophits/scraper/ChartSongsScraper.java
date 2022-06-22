package ch.simschla.swisstophits.scraper;

import ch.simschla.swisstophits.model.ChartInfo;
import ch.simschla.swisstophits.model.ChartInfo.ChartInfoBuilder;
import ch.simschla.swisstophits.model.SongInfo;
import ch.simschla.swisstophits.model.SongInfo.SongInfoBuilder;
import ch.simschla.swisstophits.scraper.pages.top50.Top50ChartsElement;
import ch.simschla.swisstophits.scraper.pages.top50.Top50Page;
import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlAnchor;
import com.gargoylesoftware.htmlunit.html.HtmlDivision;
import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class ChartSongsScraper {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChartSongsScraper.class);

    @NonNull
    private final Integer year;

    public ChartSongsScraper(@NonNull Integer year) {
        this.year = year;
    }

    public ChartInfo fetchChartInfo() {
        try (WebClient webClient = createWebClient()) {
            return fetchChartInfo(webClient);
        }
    }

    private ChartInfo fetchChartInfo(WebClient webClient) {

        ChartInfoBuilder chartInfoBuilder = ChartInfo.builder()
                .chartYear(year);


        Top50Page top50Page = Top50Page.openPage(webClient, year);
        List<Top50ChartsElement> chartsElements = top50Page.getChartsElements();
        for (int position = 0; position < chartsElements.size(); position++) {
            Top50ChartsElement top50ChartsElement = chartsElements.get(position);
            chartInfoBuilder.chartSong(chartSong(top50ChartsElement, year, position + 1));
        }

        ChartInfo chartInfo = chartInfoBuilder.build();
        LOGGER.debug("Found scraped info for year {}: {}", year, chartInfo);
        return chartInfo;
    }

    private SongInfo chartSong(@NonNull Top50ChartsElement chartsElement, @NonNull Integer year, @NonNull Integer position) {
        SongInfoBuilder songInfoBuilder = SongInfo.builder()
                .song(chartsElement.songName())
                .artists(chartsElement.artists())
                .coverImageUrl(chartsElement.coverImageUrl())
                .swissAct(chartsElement.isSwissAct())
                .chartYear(year)
                .position(position);


        return songInfoBuilder.build();
    }

    private HtmlAnchor getSongAnchor(HtmlDivision songDiv) {
        HtmlDivision songInfoDiv = songDiv.getFirstByXPath(".//div[@class='chart_title']");
        HtmlAnchor songAnchor = songInfoDiv.getFirstByXPath(".//a");
        return songAnchor;
    }

    private WebClient createWebClient() {
        WebClient webClient = new WebClient(BrowserVersion.CHROME);
        webClient.getOptions().setCssEnabled(false);
//        webClient.getOptions().setThrowExceptionOnFailingStatusCode(false);
        webClient.getOptions().setThrowExceptionOnScriptError(false);
        webClient.getOptions().setPrintContentOnFailingStatusCode(false);
        webClient.getOptions().setJavaScriptEnabled(false);
        return webClient;
    }


    public static void main(String[] args) {
        new ChartSongsScraper(1994).fetchChartInfo();
    }
}
