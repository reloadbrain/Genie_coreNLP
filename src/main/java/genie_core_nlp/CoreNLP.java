package genie_core_nlp;

import static spark.Spark.post;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Properties;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.AnnotationPipeline;
import edu.stanford.nlp.pipeline.POSTaggerAnnotator;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.pipeline.TokenizerAnnotator;
import edu.stanford.nlp.pipeline.WordsToSentencesAnnotator;
import edu.stanford.nlp.time.TimeAnnotations;
import edu.stanford.nlp.time.TimeAnnotator;
import edu.stanford.nlp.time.TimeExpression;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeCoreAnnotations;
import edu.stanford.nlp.util.CoreMap;
import spark.Request;
import spark.Response;
import spark.Route;

public class CoreNLP {
	static Properties props = null;
	static StanfordCoreNLP pipeline = null;
	static AnnotationPipeline pipeline2 = null;
	
    public static void main(String[] args) {
    	props = new Properties();
        props.put("annotators", "tokenize, ssplit, pos, lemma, parse");        
        pipeline = new StanfordCoreNLP(props);
        pipeline2 = new AnnotationPipeline();
        pipeline2.addAnnotator(new TokenizerAnnotator(true));
        pipeline2.addAnnotator(new WordsToSentencesAnnotator(true));
        pipeline2.addAnnotator(new POSTaggerAnnotator(true));
        pipeline2.addAnnotator(new TimeAnnotator("sutime", new Properties()));
        
        post("/processQuery", new Route() {
            @Override
            public Object handle(Request request, Response response) {
            	String input = request.body();
                return processData(input);
            }
        });
    }
    
    private static String processData(String input) {
        Annotation annotation;
        annotation = new Annotation(input);
        pipeline.annotate(annotation);
        String finalOutput = "";
        List<CoreMap> sentences = annotation.get(CoreAnnotations.SentencesAnnotation.class);
        if (sentences != null) {
          for (int i = 0, sz = sentences.size(); i < sz; i ++) {
            CoreMap sentence = sentences.get(i);
            String str = sentence.toString();
            Annotation ann = new Annotation(str);
            Date dt = new Date();
            SimpleDateFormat ft = new SimpleDateFormat ("yyyy-MM-dd");
            ann.set(CoreAnnotations.DocDateAnnotation.class, ft.format(dt));
            // finalOutput += ln(ann);
            pipeline2.annotate(ann);
            List<CoreMap> timexAnnsAll = ann.get(TimeAnnotations.TimexAnnotations.class);
            Integer[] start = new Integer[timexAnnsAll.size()];
            Integer[] end = new Integer[timexAnnsAll.size()];
            String[] value = new String[timexAnnsAll.size()];
            Integer count = 0;
            for (CoreMap cm : timexAnnsAll) {
              List<CoreLabel> tokens2 = cm.get(CoreAnnotations.TokensAnnotation.class);
              start[count] = Integer.parseInt(tokens2.get(0).toString().split("-")[1]);
              end[count] = Integer.parseInt(tokens2.get(tokens2.size()-1).toString().split("-")[1]);
              value[count] = cm.get(TimeExpression.Annotation.class).getTemporal().toString();
              count += 1;
            }
            Integer totalTimeExpr = count;
            List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
            String[] tokenAnnotations = {
                    "index", "word", "tag", "lemma" };
            count = 1;
            Integer index = 0;
            for (CoreLabel token: tokens) {
              // String s = token.toShorterString(tokenAnnotations);
              // if (count>=start[index] && count<=end[index]){
              // }
              finalOutput += token.index()+" "+token.word()+ " "+token.tag()+ " "+token.lemma()+" ";
              if(index<totalTimeExpr && count>=start[index] && count<=end[index]){
                // Time value
                String val = value[index];
                String type;
                if(val.contains("P")){
                  type = "DURATION";
                }
                else if(val.contains("T")){
                  type = "TIME";
                }
                else{
                  type = "DATE";
                }
                finalOutput += type+ " " +val;
                if(count==end[index]){
                  index += 1;
                }
              }
              else{          
                finalOutput += "0 0";
              }
              finalOutput += "\n";
              count += 1;
            }
            Tree tree = sentence.get(TreeCoreAnnotations.TreeAnnotation.class);
            finalOutput += "<parse>"+tree+"\n";
          }
        }
		return finalOutput;
	}
}