package lucenex;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.core.KeywordTokenizerFactory;
import org.apache.lucene.analysis.core.LowerCaseFilterFactory;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.analysis.core.WhitespaceTokenizerFactory;
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.apache.lucene.analysis.en.EnglishMinimalStemFilterFactory;
import org.apache.lucene.analysis.en.EnglishPossessiveFilterFactory;
import org.apache.lucene.analysis.it.ItalianAnalyzer;
import org.apache.lucene.analysis.miscellaneous.HyphenatedWordsFilterFactory;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.miscellaneous.TrimFilterFactory;
import org.apache.lucene.analysis.miscellaneous.WordDelimiterGraphFilterFactory;
import org.apache.lucene.analysis.pattern.PatternReplaceCharFilterFactory;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.codecs.Codec;
import org.apache.lucene.codecs.simpletext.SimpleTextCodec;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.suggest.analyzing.SuggestStopFilterFactory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.junit.Before;
import org.junit.Test;

public class Homework2 {
 
	private ArrayList<File> queue;
	private IndexWriter writer;
	private String percorso;
	@Before 
	public void setup() {
		queue= new ArrayList<File>();
		
		percorso="target/docs";

	}
	 private void addFiles(File file) {

	        if (!file.exists()) {
	            System.out.println(file + " does not exist.");
	        }
	        if (file.isDirectory()) {
	            for (File f : file.listFiles()) {
	                addFiles(f);
	            }
	        } else {
	            String filename = file.getName().toLowerCase();
	            //===================================================
	            // Only index text files
	            //===================================================
	            if (filename.endsWith(".htm") || filename.endsWith(".html") ||
	                    filename.endsWith(".xml") || filename.endsWith(".txt")) {
	                queue.add(file);
	            } else {
	                System.out.println("Skipped " + filename);
	            }
	        }
	    }


	
	private void indexDocs(String fileName, Directory directory, Codec codec) throws IOException {
		HashMap<String, String> map= new HashMap<>();
		map.put("pattern", ".txt");
		map.put("replacement", "");
		
		Analyzer myAnalyzer = CustomAnalyzer.builder()
				.withTokenizer(WhitespaceTokenizerFactory.class)
				.addTokenFilter(HyphenatedWordsFilterFactory.class)
				.addTokenFilter(EnglishPossessiveFilterFactory.class)
				.addTokenFilter(WordDelimiterGraphFilterFactory.class)
				.addTokenFilter(EnglishMinimalStemFilterFactory.class)
				.addTokenFilter(LowerCaseFilterFactory.class)
				.addTokenFilter(SuggestStopFilterFactory.class)
				.build();
		Analyzer myAnalyzer2 = CustomAnalyzer.builder()        //Analyzer per il titolo
                .addCharFilter(PatternReplaceCharFilterFactory.class, map)
                .withTokenizer(KeywordTokenizerFactory.class)
                .addTokenFilter(LowerCaseFilterFactory.class)
                .build();
		 Map<String, Analyzer> perFieldAnalyzers = new HashMap<>();
		 perFieldAnalyzers.put("contenuto", myAnalyzer);
	        perFieldAnalyzers.put("titolo", myAnalyzer2);
	        
	        Analyzer analyzer = new PerFieldAnalyzerWrapper(myAnalyzer, perFieldAnalyzers);
	        IndexWriterConfig config = new IndexWriterConfig(analyzer);
	        if (codec != null) {
	            config.setCodec(codec);
	        }
	        writer = new IndexWriter(directory, config);
	        writer.deleteAll();
	        
	        addFiles(new File(fileName));

		       
	        for (File f : queue) {
	            FileReader fr = null;
	            
	            try {
	                Document doc = new Document();
	                fr = new FileReader(f);
	                doc.add(new TextField("contenuto", fr));
	                doc.add(new TextField("titolo", f.getName(), Field.Store.YES));
	                writer.addDocument(doc);
	                //System.out.println("Added: " + f);
	            } catch (Exception e) {
	                System.out.println("Could not add: " + f);
	            } finally {
	                fr.close();
	            }
	        }

	      

	        queue.clear();
	    

	        writer.commit();
	        writer.close();
	}
	@Test
	 public void testIndexingAndSearchQP() throws Exception {
        Path path = Paths.get("target/indice");
       

        try (Directory directory = FSDirectory.open(path)) {
            Long startTime= System.nanoTime();
        	indexDocs(percorso, directory, new SimpleTextCodec());
        	Long endTime= System.nanoTime();
        	Long finish=(endTime-startTime)/1000000;
        	System.out.println("tempo di indicizzazione: "+finish);
        	Scanner scan = new Scanner(System.in);
            System.out.println("premi t per cercare nel titolo oppure c nel contenuto");
            String scelta = scan.next();
            System.out.println("inserisci la query");
            String param = scan.next();
            QueryParser parser;
            if(scelta.equals("c")) {	
            	parser = new QueryParser("contenuto", new WhitespaceAnalyzer());}
            else {
            	parser = new QueryParser("titolo", new WhitespaceAnalyzer());}
            Query queryContenuto = parser.parse(param);
            try (IndexReader reader = DirectoryReader.open(directory)) {
                IndexSearcher searcher = new IndexSearcher(reader);
                runQuery(searcher, queryContenuto);
            } finally {
                directory.close();
            }

        }
    }
	
	private void runQuery(IndexSearcher searcher, Query query) throws IOException {
        runQuery(searcher, query, false);
    }

    private void runQuery(IndexSearcher searcher, Query query, boolean explain) throws IOException {
        TopDocs hits = searcher.search(query, 10);
        for (int i = 0; i < hits.scoreDocs.length; i++) {
            ScoreDoc scoreDoc = hits.scoreDocs[i];
            Document doc = searcher.doc(scoreDoc.doc);
            System.out.println("doc"+scoreDoc.doc + ":"+ doc.get("titolo") + " (" + scoreDoc.score +")");
            if (explain) {
                Explanation explanation = searcher.explain(query, scoreDoc.doc);
                System.out.println(explanation);
            }
        }
    }


}
