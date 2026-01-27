import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class test_jsoup3 {
    public static void main(String[] args) {
        try {
            String url = "https://movie.douban.com/photos/photo/2925525013/";
            
            System.out.println("正在获取页面: " + url);
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36")
                    .timeout(30000)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "zh-CN,zh;q=0.9")
                    .referrer("https://movie.douban.com/")
                    .followRedirects(true)
                    .get();
            
            System.out.println("页面标题: " + doc.title());
            System.out.println("\n拦截页面HTML（前500字符）:");
            System.out.println(doc.html().substring(0, Math.min(500, doc.html().length())));
            System.out.println("\n...\n");
            
            // 查找按钮或脚本
            Elements buttons = doc.select("button");
            System.out.println("找到按钮: " + buttons.size() + " 个");
            for (Element btn : buttons) {
                System.out.println("  - " + btn.text() + " (id: " + btn.id() + ", class: " + btn.className() + ")");
            }
            
            Elements scripts = doc.select("script");
            System.out.println("\n找到脚本: " + scripts.size() + " 个");
            if (scripts.size() > 0) {
                System.out.println("第一个脚本内容（前200字符）:");
                String scriptContent = scripts.first().html();
                System.out.println(scriptContent.substring(0, Math.min(200, scriptContent.length())));
            }
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

