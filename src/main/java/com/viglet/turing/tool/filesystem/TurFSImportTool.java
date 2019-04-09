package com.viglet.turing.tool.filesystem;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TimeZone;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.viglet.turing.api.sn.job.TurSNJobAction;
import com.viglet.turing.api.sn.job.TurSNJobItem;
import com.viglet.turing.api.sn.job.TurSNJobItems;
import com.viglet.turing.tool.file.TurFileAttributes;
import com.viglet.turing.tool.filesystem.format.TurFormatValue;

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

	@Parameter(names = { "--driver", "-d" }, description = "Manually specify JDBC driver class to use", required = true)
	private String driver = null;

	@Parameter(names = { "--connect", "-c" }, description = "Specify JDBC connect string", required = true)
	private String connect = null;

	@Parameter(names = { "--query", "-q" }, description = "Import the results of statement", required = true)
	private String query = null;

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

	@Parameter(names = { "--multi-valued-separator" }, description = "Multi Valued Separator")
	private String mvSeparator = ",";

	@Parameter(names = { "--multi-valued-field" }, description = "Multi Valued Fields")
	private String mvField = "";

	@Parameter(names = { "--remove-html-tags-field" }, description = "Remove HTML Tags into content of field")
	public String htmlField = "";

	@Parameter(names = "--file-path-field", description = "Field with File Path", help = true)
	private String filePathField = null;

	@Parameter(names = "--file-content-field", description = "Field that shows Content of File", help = true)
	private String fileContentField = null;

	@Parameter(names = "--file-size-field", description = "Field that shows Size of File in bytes", help = true)
	private String fileSizeField = null;

	@Parameter(names = "--class-name", description = "Customized Class to modified rows", help = true)
	private String customClassName = null;

	@Parameter(names = { "--show-output", "-o" }, description = "Show Output", arity = 1)
	public boolean showOutput = false;

	@Parameter(names = { "--encoding" }, description = "Encoding Source")
	public String encoding = "UTF-8";

	@Parameter(names = "--help", description = "Print usage instructions", help = true)
	private boolean help = false;

	private static TurFormatValue turFormatValue = null;

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
			turFormatValue = new TurFormatValue(main);
			main.run();
		} catch (ParameterException e) {
			// Handle everything on your own, i.e.
			logger.info("Error: " + e.getLocalizedMessage());
			jCommander.usage();
		}

	}

	public void run() {
		logger.info(String.format("driver: %s", driver));
		logger.info(String.format("connect: %s", connect));
		logger.info(String.format("query: %s", query));
		logger.info(String.format("username: %s", username));

		this.select();
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
		Connection conn = null;
		Statement stmt = null;

		try {
			// Register JDBC driver
			Class.forName(driver);

			// Open a connection
			logger.info("Connecting to database...");
			conn = DriverManager.getConnection(connect, username, password);

			// Execute a query
			logger.info("Creating statement...");
			stmt = conn.createStatement();
			String sql;
			sql = query;
			ResultSet rs = stmt.executeQuery(sql);

			// Extract data from result set
			int chunkCurrent = 0;
			int chunkTotal = 0;
			TurSNJobItems turSNJobItems = new TurSNJobItems();

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

				// MultiValue
				String[] strMvFields = mvField.toLowerCase().split(",");
				for (Entry<String, Object> atribute : attributes.entrySet()) {
					String attributeName = atribute.getKey();
					String attributeValue = (String) atribute.getValue();
					for (String strMvField : strMvFields) {
						if (attributeName.toLowerCase().equals(strMvField.toLowerCase())) {
							if (attributeValue != null) {
								String[] mvValues = attributeValue.split(mvSeparator);
								List<String> multiValueList = new ArrayList<String>();

								for (String mvValue : mvValues) {
									multiValueList.add(turFormatValue.format(attributeName, mvValue));

								}
								attributes.put(attributeName, multiValueList);
							}
						}
					}
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
			if (chunkCurrent > 0) {

				this.sendServer(turSNJobItems, chunkTotal);
				turSNJobItems = new TurSNJobItems();
				chunkCurrent = 0;
			}
			// STEP 6: Clean-up environment
			rs.close();
			stmt.close();
			conn.close();
		} catch (SQLException se) {
			// Handle errors for JDBC
			se.printStackTrace();
		} catch (Exception e) {
			// Handle errors for Class.forName
			e.printStackTrace();
		} finally {
			// finally block used to close resources
			try {
				if (stmt != null)
					stmt.close();
			} catch (SQLException se2) {
			} // nothing we can do
			try {
				if (conn != null)
					conn.close();
			} catch (SQLException se) {
				se.printStackTrace();
			} // end finally try
		} // end try
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
