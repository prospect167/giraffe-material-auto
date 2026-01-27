import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class test_jsoup2 {
    public static void main(String[] args) {
        try {
            String url = "https://movie.douban.com/photos/photo/2925525013/";
            
            System.out.println("正在获取页面（带完整HTTP头）: " + url);
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .timeout(30000)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
                    .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                    .header("Accept-Encoding", "gzip, deflate, br")
                    .header("Connection", "keep-alive")
                    .header("Upgrade-Insecure-Requests", "1")
                    .header("Sec-Fetch-Dest", "document")
                    .header("Sec-Fetch-Mode", "navigate")
                    .header("Sec-Fetch-Site", "none")
                    .header("Cache-Control", "max-age=0")
                    .referrer("https://movie.douban.com/")
                    .followRedirects(true)
                    .get();
            
            System.out.println("页面标题: " + doc.title());
            System.out.println("\n测试选择器：");
            
            // 测试1: a.mainphoto img
            Elements test1 = doc.select("a.mainphoto img");
            System.out.println("1. a.mainphoto img: " + test1.size() + " 个");
            if (!test1.isEmpty()) {
                System.out.println("   src: " + test1.first().absUrl("src"));
            }
            
            // 测试2: div.photo-show img
            Elements test2 = doc.select("div.photo-show img");
            System.out.println("2. div.photo-show img: " + test2.size() + " 个");
            if (!test2.isEmpty()) {
                System.out.println("   src: " + test2.first().absUrl("src"));
            }
            
            // 测试3: 查找"下一张"链接
            Elements nextLinks = doc.select("a:contains(下一张)");
            System.out.println("\n3. a:contains(下一张): " + nextLinks.size() + " 个");
            for (Element link : nextLinks) {
                System.out.println("   text: " + link.text() + ", href: " + link.absUrl("href"));
            }
            
            // 测试4: 查找"前进"链接
            Elements forwardLinks = doc.select("a:contains(前进)");
            System.out.println("\n4. a:contains(前进): " + forwardLinks.size() + " 个");
            for (Element link : forwardLinks) {
                System.out.println("   text: " + link.text() + ", href: " + link.absUrl("href"));
            }
            
            // 测试5: 提取总数
            String bodyText = doc.text();
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("共(\\d+)张");
            java.util.regex.Matcher matcher = pattern.matcher(bodyText);
            if (matcher.find()) {
                System.out.println("\n5. 总图片数: " + matcher.group(1));
            } else {
                System.out.println("\n5. 未找到总图片数");
            }
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

