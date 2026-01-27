import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class test_jsoup {
    public static void main(String[] args) {
        try {
            String url = "https://movie.douban.com/photos/photo/2925525013/";
            
            System.out.println("正在获取页面: " + url);
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(30000)
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
            
            // 测试3: 所有包含doubanio的图片
            Elements test3 = doc.select("img[src*=doubanio]");
            System.out.println("3. img[src*=doubanio]: " + test3.size() + " 个");
            for (int i = 0; i < Math.min(5, test3.size()); i++) {
                System.out.println("   [" + i + "] " + test3.get(i).absUrl("src"));
            }
            
            // 测试4: 查找"下一张"链接
            Elements nextLinks = doc.select("a:contains(下一张)");
            System.out.println("\n4. a:contains(下一张): " + nextLinks.size() + " 个");
            for (Element link : nextLinks) {
                System.out.println("   text: " + link.text() + ", href: " + link.absUrl("href"));
            }
            
            // 测试5: 查找"前进"链接
            Elements forwardLinks = doc.select("a:contains(前进)");
            System.out.println("\n5. a:contains(前进): " + forwardLinks.size() + " 个");
            for (Element link : forwardLinks) {
                System.out.println("   text: " + link.text() + ", href: " + link.absUrl("href"));
            }
            
            // 测试6: 提取总数
            String bodyText = doc.text();
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("共(\\d+)张");
            java.util.regex.Matcher matcher = pattern.matcher(bodyText);
            if (matcher.find()) {
                System.out.println("\n6. 总图片数: " + matcher.group(1));
            } else {
                System.out.println("\n6. 未找到总图片数");
            }
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

