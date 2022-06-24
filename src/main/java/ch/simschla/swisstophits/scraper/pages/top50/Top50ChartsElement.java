package ch.simschla.swisstophits.scraper.pages.top50;

import ch.simschla.swisstophits.scraper.pages.PageObjectElement;
import com.gargoylesoftware.htmlunit.html.HtmlAnchor;
import com.gargoylesoftware.htmlunit.html.HtmlBold;
import com.gargoylesoftware.htmlunit.html.HtmlDivision;
import lombok.NonNull;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;

public class Top50ChartsElement implements PageObjectElement {

    @NonNull
    private final Top50Page parentPage;

    @NonNull
    private final HtmlDivision element;

    public Top50ChartsElement(Top50Page parentPage, HtmlDivision element) {
        this.parentPage = parentPage;
        this.element = element;
    }

    public static Top50ChartsElement onPage(@NonNull Top50Page top50Page, @NonNull HtmlDivision div) {
        return new Top50ChartsElement(top50Page, div);
    }


    public Boolean isSwissAct() {
        return element.getFirstByXPath(".//img[contains(@src, 'swiss')]") != null;
    }

    private HtmlAnchor getSongAnchor() {
        HtmlDivision songInfoDiv = element.getFirstByXPath(".//div[@class='chart_title']");
        HtmlAnchor songAnchor = songInfoDiv.getFirstByXPath(".//a");
        return songAnchor;
    }

    public String songName() {
        HtmlAnchor songAnchor = getSongAnchor();
        return songAnchor.asNormalizedText().split("\n")[1].trim();
    }

    public Collection<String> artists() {
        HtmlAnchor songAnchor = getSongAnchor();
        HtmlBold bold = songAnchor.getFirstByXPath(".//b");
        return parseArtists(bold.asNormalizedText());
    }

    private Collection<String> parseArtists(String artistsText) {
        return Arrays.stream(artistsText.split(", | & | \\+ | / | feat. | featuring | Feat. | Featuring | and | und "))
                .filter(Objects::nonNull)
                .map(String::trim)
                .map(s -> s.length() == 0 ? null : s)
                .filter(Objects::nonNull)
                .toList();
    }

    public URL coverImageUrl() {
        try {
            HtmlDivision songCoverDiv = element.getFirstByXPath(".//div[@class='chart_cover']");
            String background_url = songCoverDiv.getStyleElement("background").getValue();
            return new URL(background_url.split("\"")[1]);
        } catch (MalformedURLException e) {
            return null;
        }
    }
}
