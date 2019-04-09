package com.viglet.turing.tool.filesystem;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.viglet.turing.api.sn.job.TurSNJobItems;
import com.viglet.turing.tool.file.TurFileAttributes;

import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.SAXException;

public class TurFSImportTool {
	static final Logger logger = LogManager.getLogger(TurFSImportTool.class.getName());
	
	@Parameter(names = "--file-path", description = "Field with File Path", help = true)
	private String filePath = null;
	
	@Parameter(names = { "--site" }, description = "Specify the Semantic Navigation Site", required = true)
	private String site = null;

	@Parameter(names = { "--server", "-s" }, description = "Viglet Turing Server")
	private String turingServer = "http://localhost:2700";

	@Parameter(names = { "--username", "-u" }, description = "Set authentication username")
	private String username = null;

	@Parameter(names = { "--password", "-p" }, description = "Set authentication password")
	private String password = null;

	@Parameter(names = { "--type", "-t" }, description = "Set Content Type name")
	public String type = "CONTENT_TYPE";

	@Parameter(names = { "--chunk", "-z" }, description = "Number of items to be sent to the queue")
	private int chunk = 100;

	@Parameter(names = { "--include-type-in-id", "-i" }, description = "Include Content Type name in Id", arity = 1)
	public boolean typeInId = false;

	@Parameter(names = "--file-content-field", description = "Field that shows Content of File", help = true)
	private String fileContentField = null;

	@Parameter(names = "--file-size-field", description = "Field that shows Size of File in bytes", help = true)
	private String fileSizeField = null;

	@Parameter(names = { "--show-output", "-o" }, description = "Show Output", arity = 1)
	public boolean showOutput = false;

	@Parameter(names = { "--encoding" }, description = "Encoding Source")
	public String encoding = "UTF-8";

	@Parameter(names = "--help", description = "Print usage instructions", help = true)
	private boolean help = false;

	public static void main(String... argv) {

		TurFSImportTool main = new TurFSImportTool();
		JCommander jCommander = JCommander.newBuilder().addObject(main).build();
		try {
			jCommander.parse(argv);
			if (main.help) {
				jCommander.usage();
				return;
			}
			System.out.println("Viglet Turing Filesystem Import Tool.");
			main.run();
		} catch (ParameterException e) {
			// Handle everything on your own, i.e.
			logger.info("Error: " + e.getLocalizedMessage());
			jCommander.usage();
		}

	}

	public void run() {
		Path startPath = Paths.get(filePath);

		try {
			Files.walkFileTree(startPath, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE,
					new SimpleFileVisitor<Path>() {
						@Override
						public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {							
							System.out.println(file.toAbsolutePath().toString());
							TurFileAttributes turFileAttributes = readFile(file.toAbsolutePath().toString());
							System.out.println(cleanTextContent(turFileAttributes.getContent()));
							return FileVisitResult.CONTINUE;
						}
					});
		} catch (IOException ioe) {
			logger.error(ioe);
		}

		this.select();
	}

	 private static String cleanTextContent(String text)
	    {
	        // strips off all non-ASCII characters
	        text = text.replaceAll("[^\\x00-\\x7F]", "");
	 
	        // erases all the ASCII control characters
	        text = text.replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", "");
	         
	        // removes non-printable characters from Unicode
	        text = text.replaceAll("\\p{C}", "");
	 
	        return text.trim();
	    }
	 
	private TurFileAttributes readFile(String filePath) {

		try {
			File file = new File(filePath);
			if (file.exists()) {
				InputStream inputStream = new FileInputStream(file);

				AutoDetectParser parser = new AutoDetectParser();
				// -1 = no limit of number of characters
				BodyContentHandler handler = new BodyContentHandler(-1);
				Metadata metadata = new Metadata();

				ParseContext pcontext = new ParseContext();

				parser.parse(inputStream, handler, metadata, pcontext);
				TurFileAttributes turFileAttributes = new TurFileAttributes();
				turFileAttributes.setContent(handler.toString());
				turFileAttributes.setFile(file);
				turFileAttributes.setMetadata(metadata);

				return turFileAttributes;
			} else {
				logger.info("File not exists: " + filePath);
			}
		} catch (IOException | SAXException | TikaException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;

	}

	public void select() {

		try {

			int chunkCurrent = 0;
			int chunkTotal = 0;
			TurSNJobItems turSNJobItems = new TurSNJobItems();
/*
			while (rs.next()) {
				TurSNJobItem turSNJobItem = new TurSNJobItem();
				turSNJobItem.setTurSNJobAction(TurSNJobAction.CREATE);
				Map<String, Object> attributes = new HashMap<String, Object>();

				ResultSetMetaData rsmd = rs.getMetaData();

				// Retrieve by column name
				for (int c = 1; c <= rsmd.getColumnCount(); c++) {
					String nameSensitve = rsmd.getColumnLabel(c);
					String className = rsmd.getColumnClassName(c);

					if (className.equals("java.lang.Integer")) {
						int intValue = rs.getInt(c);
						attributes.put(nameSensitve, turFormatValue.format(nameSensitve, Integer.toString(intValue)));
					} else if (className.equals("java.sql.Timestamp")) {
						TimeZone tz = TimeZone.getTimeZone("UTC");
						DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
						df.setTimeZone(tz);
						attributes.put(nameSensitve, turFormatValue.format(nameSensitve, df.format(rs.getDate(c))));
					} else {
						String strValue = rs.getString(c);
						attributes.put(nameSensitve, turFormatValue.format(nameSensitve, strValue));
					}
				}
				attributes.put("type", type);

				if (filePathField != null && attributes.containsKey(filePathField)) {
					TurFileAttributes turFileAttributes = this.readFile((String) attributes.get(filePathField));
					if (turFileAttributes != null) {
						if (fileSizeField != null && turFileAttributes.getFile() != null) {
							attributes.put(fileSizeField, turFileAttributes.getFile().length());
						} else {
							logger.info("File without size: " + filePathField);
						}

						if (fileContentField != null) {
							attributes.put(fileContentField, turFileAttributes.getContent());
						} else {
							logger.info("File without content: " + filePathField);
						}

					} else
						logger.info("turFileAttributes is null: " + filePathField);
				}

				turSNJobItem.setAttributes(attributes);

				turSNJobItems.add(turSNJobItem);

				chunkTotal++;
				chunkCurrent++;
				if (chunkCurrent == chunk) {
					this.sendServer(turSNJobItems, chunkTotal);
					turSNJobItems = new TurSNJobItems();
					chunkCurrent = 0;
				}
			}
			*/
			if (chunkCurrent > 0) {

				this.sendServer(turSNJobItems, chunkTotal);
				turSNJobItems = new TurSNJobItems();
				chunkCurrent = 0;
			}
	
		} catch (Exception e) {
			logger.error(e);
		}
	}

	public void sendServer(TurSNJobItems turSNJobItems, int chunkTotal) throws ClientProtocolException, IOException {
		ObjectMapper mapper = new ObjectMapper();
		String jsonResult = mapper.writeValueAsString(turSNJobItems);
		int initial = 1;
		if (chunkTotal > chunk) {
			initial = chunkTotal - chunk;
		}

		Charset utf8Charset = Charset.forName("UTF-8");
		Charset customCharset = Charset.forName(encoding);

		ByteBuffer inputBuffer = ByteBuffer.wrap(jsonResult.getBytes());

		// decode UTF-8
		CharBuffer data = utf8Charset.decode(inputBuffer);

		// encode
		ByteBuffer outputBuffer = customCharset.encode(data);

		byte[] outputData = new String(outputBuffer.array()).getBytes("UTF-8");
		String jsonUTF8 = new String(outputData);

		System.out.print("Importing " + initial + " to " + chunkTotal + " items\n");
		CloseableHttpClient client = HttpClients.createDefault();
		HttpPost httpPost = new HttpPost(String.format("%s/api/sn/%s/import", turingServer, site));
		if (showOutput) {
			System.out.println(jsonUTF8);
		}
		StringEntity entity = new StringEntity(new String(jsonUTF8), "UTF-8");
		httpPost.setEntity(entity);
		httpPost.setHeader("Accept", "application/json");
		httpPost.setHeader("Content-type", "application/json");
		httpPost.setHeader("Accept-Encoding", "UTF-8");

		@SuppressWarnings("unused")
		CloseableHttpResponse response = client.execute(httpPost);

		client.close();
	}
}
