package transfer;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Whitelist;

public class Client {
	static String string_index = "string index";
	static String passage = "data/annotation_dataset_0.txt";

	public Param parameter;

	// Top K
	private static int K = 5;

	public static Index index;

	public Client() {
		index = new Index();

	}

	public void setIndexfile(String indexfile) {
		index.setIndexfile(indexfile);
	}

	public Object init(int type) throws Throwable {
		try {
			if (type == Index.STRING_BUILD)
				index.init_building();
			else if (type == Index.STRING_SEARCH)
				index.init_query();
			else
				System.out.println("Initialization error");
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	public Object addDocHandler(long id_long, int part, String value_string) {

		index.addDoc(id_long, part, value_string);
		return null;
	}

	public ReturnValue queryHandler(List<QueryConfig> qlist) {
		try {
			return index.generalSearch(qlist);
		} catch (Throwable e) {
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * test the insertion
	 * 
	 * @throws IOException
	 * */
	private void testInsertion(String filename, String index_file, String path)
			throws Throwable {
		this.setIndexfile(index_file);
		init(Index.STRING_BUILD);
		try {
			BufferedReader buf = new BufferedReader(new InputStreamReader(
					new FileInputStream(filename)));
			String line;
			String id;

			while ((line = buf.readLine()) != null) {
				id = line;
				String str = buf.readLine();
				str = Jsoup.clean(str, "", Whitelist.none(),
						new Document.OutputSettings().prettyPrint(false));
				if (str.length() > 255) {
					str = str.substring(0, 255);
				}

				WordTokenizer wt = new WordTokenizer(new StopWords(path));
				Annotation anno = wt.getIndexAnno(str);

				if (anno.hasFirst()) {
					addDocHandler(Long.parseLong(id), 0, anno.getFirstPart());
				}
				if (anno.hasSecond()) {
					addDocHandler(Long.parseLong(id), 1, anno.getSecondPart());
				}
			}
			buf.close();
		} catch (Exception e) {
			System.out.println("{status:500, \"results\":" + e.getMessage()
					+ "}");
		}
		index.closeWriter();
	}

	private void testInsertionString(String id, String str, String index_file,
			String path) throws Throwable {
		this.setIndexfile(index_file);
		this.init(Index.STRING_BUILD);
		try {

			WordTokenizer wt = new WordTokenizer(new StopWords(path));
			str = URLDecoder.decode(str, "UTF-8");
			str = Jsoup.clean(str, "", Whitelist.none(),
					new Document.OutputSettings().prettyPrint(false));
			if (str.length() > 255) {
				str = str.substring(0, 255);
			}

			Annotation anno = wt.getIndexAnno(str);
			if (anno.hasFirst()) {
				this.addDocHandler(Long.parseLong(id), 0, anno.getFirstPart());
			}
			if (anno.hasSecond()) {
				this.addDocHandler(Long.parseLong(id), 1, anno.getSecondPart());
			}
		} catch (Exception e) {
			System.out.println("{status:500, \"results\":" + e.getMessage()
					+ "}");
		}
		index.closeWriter();
	}

	private void testDocSearch(String doc, String index_file, int K, String path)
			throws Throwable {
		this.setIndexfile(index_file);
		this.init(Index.STRING_SEARCH);
		WordTokenizer wt = new WordTokenizer(new StopWords(path));

		BufferedReader buf = new BufferedReader(new InputStreamReader(
				new FileInputStream(doc)));
		String line = new String();
		String qstr = new String();
		while ((line = buf.readLine()) != null) {
			qstr += " " + line;
		}
		buf.close();

		SearchQuery sQuery = null;

		String extension = doc.substring(doc.lastIndexOf(".") + 1);
		if (extension.equalsIgnoreCase("html")) {
			sQuery = wt.processQueryHtmlString(qstr);
		} else {
			sQuery = wt.processQueryStr(qstr);
		}

		if (sQuery == null) {
			System.out.println(Messager.UNKNOWN_ERROR);
			return;
		}
		List<QueryConfig> configs = new ArrayList<QueryConfig>();

		for (int i = 0; i < sQuery.keywordSpace.size(); i++) {
			String word = sQuery.keywordSpace.get(i);
			QueryConfig config = new STRConfig(0, sQuery, word);
			config.setK(K);
			configs.add(config);
		}

		ReturnValue revalue = queryHandler(configs);
		System.out.println(CandidatesVerifier.verifyCandidates(revalue.sQuery));
	}

	public static void main(String[] args) throws Throwable {

		Options options = new Options();

		options.addOption("index", false,
				"build index using the given index file");
		options.addOption("search", false, "search");
		options.addOption("document", false, "search using given file");
		options.addOption("keyword", false, "keyword search");
		options.addOption("s", true, "urlencoded string");
		options.addOption("f", true, "file");
		options.addOption("d", true, "anno");
		options.addOption("k", true, "k");
		options.addOption("id", true, "0");
		options.addOption("textstring", true, "");

		Client client = new Client();

		if (args.length < 3) {
			System.out.println(Messager.BAD_REQUEST);
			System.exit(-1);
		}

		CommandLineParser parser = new BasicParser();
		try {

			CommandLine cmd = parser.parse(options, args);

			if (cmd.hasOption("k")) {
				K = Integer.valueOf(cmd.getOptionValue("k"));
			}

			String anno_set = null;
			if (!cmd.hasOption("d")) {
				anno_set = "annotation_index";
			} else {
				anno_set = cmd.getOptionValue("d");
			}

			if (cmd.hasOption("index")) {
				try {
					if (cmd.hasOption("f")) {
						String file = cmd.getOptionValue("f");
						client.testInsertion(file, anno_set,
								System.getProperty("user.dir")
										+ "/wordlist.txt");
						System.out
								.println("{\"status\":200, \"results\":\"OK\"}");
					} else if (cmd.hasOption("id")
							&& cmd.hasOption("textstring")) {
						String id = cmd.getOptionValue("id");
						String str = cmd.getOptionValue("textstring");
						client.testInsertionString(id, str, anno_set,
								System.getProperty("user.dir")
										+ "/wordlist.txt");
						System.out
								.println("{\"status\":200, \"results\":\"OK\"}");
					}
				} catch (Exception e) {
					System.out.println("{status:500, \"results\":"
							+ e.getMessage() + "}");
				}
			} else if (cmd.hasOption("search")) {
				if (cmd.hasOption("document") && cmd.hasOption("f")) {
					String file = cmd.getOptionValue("f");
					client.testDocSearch(file, anno_set, K,
							System.getProperty("user.dir") + "/wordlist.txt");
				}
			} else {
				System.out.println(Messager.BAD_REQUEST);
			}
		} catch (ParseException exp) {
			System.out.println(Messager.BAD_REQUEST);
		}
	}
}

class STRConfig extends QueryConfig {

	public STRConfig(int i, SearchQuery sQuery, String string) {
		super(i, QueryConfig.DOC, sQuery, string);
	}

	public STRConfig(int i, String string) {
		super(i, QueryConfig.STRING, string);
	}

	@Override
	public float calcDistance(long a, long b) {
		return 0;
	}

	@Override
	public int getType() {
		return this.type;
	}
}
