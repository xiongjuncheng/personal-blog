package com.xjc.lucene;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.highlight.Fragmenter;
import org.apache.lucene.search.highlight.Highlighter;
import org.apache.lucene.search.highlight.InvalidTokenOffsetsException;
import org.apache.lucene.search.highlight.QueryScorer;
import org.apache.lucene.search.highlight.SimpleHTMLFormatter;
import org.apache.lucene.search.highlight.SimpleSpanFragmenter;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import com.opensymphony.oscache.util.StringUtil;
import com.xjc.model.Blog;
import com.xjc.service.BlogService;

/**
 * 用于全文检索的博客索引操作类
 */
public class BlogIndex {

	private static final String dirPath = "C:/lucene";
	private Directory dir; //存储索引的目录

	public BlogIndex() {
		try {
			dir = FSDirectory.open(Paths.get(dirPath));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * 获取IndexWriter对象
	 * @throws IOException
	 */
	private IndexWriter getWriter() throws IOException{
		
		Analyzer analyzer = new StandardAnalyzer();
		IndexWriterConfig config = new IndexWriterConfig(analyzer);
		return new IndexWriter(dir, config);
	}
	
	/**
	 * 创建索引
	 * @param blog 博客对象
	 * @throws IOException
	 */
	public void createIndex(Blog blog) throws IOException{
		
		IndexWriter writer = getWriter();
		Document doc = new Document();
		doc.add(new StringField("id", String.valueOf(blog.getId()), Field.Store.YES));
		
		//将博客的标题和内容作为索引存储到磁盘文件中
		doc.add(new TextField("title", blog.getTitle(), Field.Store.YES));
		doc.add(new TextField("content", blog.getContent(), Field.Store.YES));
		writer.addDocument(doc);
		//关闭writer
		writer.close();
	}
	
	/**
	 * 更新索引
	 * @param blog 博客对象
	 * @throws IOException
	 */
	public void updateIndex(Blog blog) throws IOException{
		
		IndexWriter writer = getWriter();
		Document doc = new Document();
		doc.add(new StringField("id", String.valueOf(blog.getId()), Field.Store.YES));
		
		//将博客的标题和内容作为索引存储到磁盘文件中
		doc.add(new TextField("title", blog.getTitle(), Field.Store.YES));
		doc.add(new TextField("content", blog.getContent(), Field.Store.YES));
		writer.updateDocument(new Term("id",String.valueOf(blog.getId())),doc);
		//关闭writer
		writer.close();
	}
	
	/**
	 * 根据博客id删除索引
	 * @param blog 博客对象
	 * @throws IOException
	 */
	public void deleteIndex(String blogId) throws IOException{
		
		IndexWriter writer = getWriter();
		writer.deleteDocuments(new Term("id",blogId));
		writer.forceMergeDeletes(); 
		writer.commit();
		//关闭writer
		writer.close();
	}
	
	/**
	 * 根据关键字索引出相关博客
	 * @param q 查询条件
	 * @return
	 * @throws InvalidTokenOffsetsException 
	 * @throws IOException 
	 */
	@SuppressWarnings("resource")
	public List<Blog> query(String q) throws Exception{
		
		//获取IndexReader对象
		IndexReader reader = DirectoryReader.open(dir);

		// 构造搜索器IndexSearcher
		IndexSearcher searcher = new IndexSearcher(reader);

		// 使用BooleanQuery实现多个Query的组合查询
		BooleanQuery.Builder booleanQuery = new BooleanQuery.Builder();
		Analyzer analyzer = new StandardAnalyzer();
		QueryParser parser = new QueryParser("title", analyzer);
		Query query = parser.parse(q);
		QueryParser parser2 = new QueryParser("content", analyzer);
		Query query2 = parser2.parse(q);
		booleanQuery.add(query, BooleanClause.Occur.SHOULD);
		booleanQuery.add(query2, BooleanClause.Occur.SHOULD);
		
		// 查询结果信息类
		TopDocs hits = searcher.search(booleanQuery.build(), 100);// 最多返回100个document
		QueryScorer scorer = new QueryScorer(query);
		Fragmenter fragmenter = new SimpleSpanFragmenter(scorer);

		// 用这个进行高亮显示，默认是<b>..</b>
		SimpleHTMLFormatter simpleHTMLFormatter = new SimpleHTMLFormatter("<b><font color='red'>", "</font></b>");
		// 构造高亮:指定高亮的格式,指定查询评分QueryScorer
		Highlighter highlighter = new Highlighter(simpleHTMLFormatter, scorer);
		highlighter.setTextFragmenter(fragmenter);

		List<Blog> blogList = new LinkedList<Blog>();
		for (ScoreDoc scoreDoc : hits.scoreDocs) {
			Document doc = searcher.doc(scoreDoc.doc);
			
			BlogService blogService = (BlogService) new ClassPathXmlApplicationContext("applicationContext.xml").getBean("blogService");
			Blog blog = blogService.findById(Integer.parseInt(doc.get(("id"))));
			String title = doc.get("title");
			String content = StringEscapeUtils.escapeHtml4(doc.get("content"));
			if (title != null) {
				TokenStream tokenStream = analyzer.tokenStream("title", new StringReader(title));
				String hTitle = highlighter.getBestFragment(tokenStream, title);
				if (StringUtil.isEmpty(hTitle))
					blog.setTitle(title);
				else
					blog.setTitle(hTitle);
			}
			if (content != null) {
				TokenStream tokenStream = analyzer.tokenStream("content", new StringReader(content));
				String hContent = highlighter.getBestFragment(tokenStream, content);
				if (StringUtil.isEmpty(hContent)){
					blog.setContent(content);
					if (content.length() <= 300)
						blog.setSummary(content);
					else
						blog.setSummary(content.substring(0, 300));
				}
				else{
					if (hContent.length() <= 300)
						blog.setSummary(hContent);
					else
						blog.setSummary(hContent.substring(0, 300));
					blog.setContent(hContent);
				}
				
			}
			blogList.add(blog);
		}
		return blogList;
	}
}