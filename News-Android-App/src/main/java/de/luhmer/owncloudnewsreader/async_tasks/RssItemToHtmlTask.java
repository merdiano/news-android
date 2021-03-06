package de.luhmer.owncloudnewsreader.async_tasks;

import android.content.Context;
import android.os.AsyncTask;
import android.text.format.DateUtils;

import com.nostra13.universalimageloader.cache.disc.DiskCache;
import com.nostra13.universalimageloader.core.ImageLoader;

import org.apache.commons.lang3.StringEscapeUtils;

import java.io.File;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.luhmer.owncloudnewsreader.R;
import de.luhmer.owncloudnewsreader.database.model.Feed;
import de.luhmer.owncloudnewsreader.database.model.RssItem;
import de.luhmer.owncloudnewsreader.helper.ColorHelper;
import de.luhmer.owncloudnewsreader.helper.ImageHandler;
import de.luhmer.owncloudnewsreader.helper.ThemeChooser;


public class RssItemToHtmlTask extends AsyncTask<Void, Void, String> {

    public interface Listener {
        /**
         * The RSS item has successfully been parsed.
         * @param htmlPage  RSS item as HTML string
         */
        void onRssItemParsed(String htmlPage);
    }


    private Context mContext;
    private RssItem mRssItem;
    private Listener mListener;


    public RssItemToHtmlTask(Context context, RssItem rssItem, Listener listener) {
        this.mContext = context;
        this.mRssItem = rssItem;
        this.mListener = listener;
    }

    @Override
    protected String doInBackground(Void... params) {
        return getHtmlPage(mContext, mRssItem, true);
    }

    @Override
    protected void onPostExecute(String htmlPage) {
        mListener.onRssItemParsed(htmlPage);
        super.onPostExecute(htmlPage);
    }


    /**
     * @param context
     * @param rssItem       item to parse
     * @param showHeader    true if a header with item title, feed title, etc. should be included
     * @return given RSS item as full HTML page
     */
    public static String getHtmlPage(Context context, RssItem rssItem, boolean showHeader) {
        String feedTitle = "Undefined";
        String favIconUrl = null;

        Feed feed = rssItem.getFeed();
        int[] colors = ColorHelper.getColorsFromAttributes(context,
                R.attr.dividerLineColor,
                R.attr.rssItemListBackground);

        int feedColor = colors[0];
        if (feed != null) {
            feedTitle = StringEscapeUtils.escapeHtml4(feed.getFeedTitle());
            favIconUrl = feed.getFaviconUrl();
            if(feed.getAvgColour() != null) {
                feedColor = Integer.parseInt(feed.getAvgColour());
            }
        }

        if (favIconUrl != null) {
            DiskCache diskCache = ImageLoader.getInstance().getDiskCache();
            File file = diskCache.get(favIconUrl);
            if(file != null) {
                favIconUrl = "file://" + file.getAbsolutePath();
            }
        } else {
            favIconUrl = "file:///android_res/drawable/default_feed_icon_light.png";
        }

        String body_id;
        switch(ThemeChooser.getInstance(context).getSelectedTheme(context, false)) {
            case 0:
                body_id = "darkTheme";
                break;
            case 1:
                body_id = "lightTheme";
                break;
            case 2:
                body_id = "darkThemeOLED";
                break;
            default:
                body_id = "darkTheme";
        }

        boolean isRightToLeft = context.getResources().getBoolean(R.bool.is_right_to_left);
        String rtlClass = isRightToLeft ? "rtl" : "";
        String borderSide = isRightToLeft ? "right" : "left";

        StringBuilder builder = new StringBuilder();

        builder.append("<html><head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1, maximum-scale=1, minimum-scale=1, user-scalable=0\" />");
        builder.append("<link rel=\"stylesheet\" type=\"text/css\" href=\"web.css\" />");
        builder.append("<style type=\"text/css\">");
        builder.append(String.format(
                        "#top_section { border-%s: 4px solid %s; border-bottom: 1px solid %s; background: %s }",
                        borderSide,
                        ColorHelper.getCssColor(feedColor),
                        ColorHelper.getCssColor(colors[0]),
                        ColorHelper.getCssColor(colors[1]))
        );
        builder.append("</style>");
        builder.append(String.format("</head><body id=\"%s\" class=\"%s\">", body_id, rtlClass));

        if (showHeader) {
            builder.append("<div id=\"top_section\">");
            builder.append("<div id=\"header\">");
            String title = StringEscapeUtils.escapeHtml4(rssItem.getTitle());
            String linkToFeed = StringEscapeUtils.escapeHtml4(rssItem.getLink());
            builder.append(String.format("<a href=\"%s\">%s</a>", linkToFeed, title));
            builder.append("</div>");

            String authorOfArticle = StringEscapeUtils.escapeHtml4(rssItem.getAuthor());
            if (authorOfArticle != null)
                if (!authorOfArticle.trim().equals(""))
                    feedTitle += " - " + authorOfArticle.trim();

            builder.append("<div id=\"header_small_text\">");

            builder.append("<div id=\"subscription\">");
            builder.append(String.format("<img id=\"imgFavicon\" src=\"%s\" />", favIconUrl));
            builder.append(feedTitle.trim());
            builder.append("</div>");

            Date date = rssItem.getPubDate();
            if (date != null) {
                String dateString = (String) DateUtils.getRelativeTimeSpanString(date.getTime());
                builder.append("<div id=\"datetime\">");
                builder.append(dateString);
                builder.append("</div>");
            }

            builder.append("</div>");

            builder.append("</div>");
        }

        String description = rssItem.getBody();

        description = getDescriptionWithCachedImages(description).trim();
        description = removePreloadAttributeFromVideos(description);

        builder.append("<div id=\"content\">");
        builder.append(description);
        builder.append("</div>");

        builder.append("</body></html>");

        return builder.toString().replaceAll("\"//", "\"https://");
    }

    private static String getDescriptionWithCachedImages(String text) {
        List<String> links = ImageHandler.getImageLinksFromText(text);
        DiskCache diskCache = ImageLoader.getInstance().getDiskCache();

        for(String link : links) {
            link = link.trim();
            try {
                File file = diskCache.get(link);
                if(file != null)
                    text = text.replace(link, "file://" + file.getAbsolutePath());
            } catch(Exception ex) {
                ex.printStackTrace();
            }
        }

        return text;
    }

    private static Pattern PATTERN_PRELOAD_VIDEOS = Pattern.compile("(<video[^>]*)(preload=\".*?\")");
    private static String removePreloadAttributeFromVideos(String text) {
        Matcher m = PATTERN_PRELOAD_VIDEOS.matcher(text);
        if(m.find()) {
            StringBuffer sb = new StringBuffer();
            do {
                //$1 represents the 1st group
                m.appendReplacement(sb, "$1" + "preload=\"none\"");
            } while (m.find());
            m.appendTail(sb);
            text = sb.toString();
        }
        return text;
    }
}
