package ch.simschla.swisstophits.scraper.pages.top50;

import ch.simschla.swisstophits.scraper.exception.ScrapingException;
import ch.simschla.swisstophits.scraper.pages.PageObject;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlDivision;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import lombok.NonNull;

public class Top50Page implements PageObject {

    public static final String BASE_URL = "https://hitparade.ch/charts/jahreshitparade/";

    @NonNull
    private final WebClient webClient;

    @NonNull
    private final HtmlPage page;

    @NonNull
    private final URL pageUrl;

    public Top50Page(@NonNull WebClient webClient, @NonNull URL pageUrl) {
        try {
            this.webClient = webClient;
            this.pageUrl = pageUrl;
            this.page = webClient.getPage(pageUrl);
        } catch (IOException e) {
            throw ScrapingException.wrap(e);
        }
    }

    public static Top50Page openPage(@NonNull WebClient webClient, @NonNull Integer year) {
        try {
            return new Top50Page(webClient, new URL(BASE_URL + year));
        } catch (MalformedURLException e) {
            throw ScrapingException.wrap(e);
        }
    }

    public List<Top50ChartsElement> getChartsElements() {
        return page.<HtmlDivision>getByXPath("//div[@class='main']/div[@class='content']").stream()
                .map(div -> Top50ChartsElement.onPage(this, div))
                .toList();
    }
}
