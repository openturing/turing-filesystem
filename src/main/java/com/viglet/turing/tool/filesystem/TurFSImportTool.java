package com.viglet.turing.tool.filesystem;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
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
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.viglet.turing.api.sn.job.TurSNJobAction;
import com.viglet.turing.api.sn.job.TurSNJobItem;
import com.viglet.turing.api.sn.job.TurSNJobItems;
import com.viglet.turing.tool.file.TurFileAttributes;

import org.apache.commons.io.FilenameUtils;
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

	private final static String EOL = " ";
	int chunkCurrent = 0;
	int chunkTotal = 0;
	TurSNJobItems turSNJobItems = new TurSNJobItems();

	@Parameter(names = "--file-path", description = "Field with File Path", required = true)
	private String filePath = null;

	@Parameter(names = "--prefix-from-replace", description = "Prefix from Replace", required = true)
	private String prefixFromReplace = null;

	@Parameter(names = "--prefix-to-replace", description = "Prefix to Replace", required = true)
	private String prefixToReplace = null;

	@Parameter(names = { "--site" }, description = "Specify the Semantic Navigation Site", required = true)
	private String site = null;

	@Parameter(names = { "--server", "-s" }, description = "Viglet Turing Server", required = true)
	private String turingServer = "http://localhost:2700";

	@Parameter(names = { "--type", "-t" }, description = "Set Content Type name")
	public String type = "CONTENT_TYPE";

	@Parameter(names = { "--chunk", "-z" }, description = "Number of items to be sent to the queue")
	private int chunk = 100;

	@Parameter(names = { "--include-type-in-id", "-i" }, description = "Include Content Type name in Id", arity = 1)
	public boolean typeInId = false;

	@Parameter(names = "--file-content-field", description = "Field that shows Content of File", help = true)
	private String fileContentField = "text";

	@Parameter(names = "--file-size-field", description = "Field that shows Size of File in bytes", help = true)
	private String fileSizeField = "fileSize";

	@Parameter(names = "--file-extension-field", description = "Field that shows extension of File", help = true)
	private String fileExtensionField = "fileExtension";

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
						public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
							File file = new File(path.toAbsolutePath().toString());
							TurSNJobItem turSNJobItem = new TurSNJobItem();
							turSNJobItem.setTurSNJobAction(TurSNJobAction.CREATE);
							Map<String, Object> attributes = new HashMap<String, Object>();

							List<String> imagesExtensions = new ArrayList<String>(
									Arrays.asList("bmp", "pnm", "png", "jfif", "jpg", "jpeg", "tiff"));
							List<String> webImagesExtensions = new ArrayList<String>(
									Arrays.asList("pnm", "png", "jpg", "jpeg", "gif"));
							String extension = FilenameUtils.getExtension(file.getAbsolutePath()).toLowerCase();
							if (!extension.equals("ds_store")) {
								String content = imagesExtensions.contains(extension) ? extractTextFromImage(file)
										: extractTextFromFile(file);
								TimeZone tz = TimeZone.getTimeZone("UTC");
								DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
								df.setTimeZone(tz);
								String fileURL = file.getAbsolutePath();
								if (prefixFromReplace != null && prefixToReplace != null)
									fileURL = fileURL.replace(prefixFromReplace, prefixToReplace);
								if (typeInId)
									attributes.put("id", type + fileURL);
								else
									attributes.put("id", fileURL);
								attributes.put("date", df.format(file.lastModified()));
								attributes.put("title", file.getName());
								attributes.put("type", type);
								if (fileContentField != null)
									attributes.put(fileContentField, content);
								if (webImagesExtensions.contains(extension))
									attributes.put("image", fileURL);
								if (fileExtensionField != null)
									attributes.put(fileExtensionField, extension);
								if (fileSizeField != null)
									attributes.put(fileSizeField, file.length());
								attributes.put("url", fileURL);
								turSNJobItem.setAttributes(attributes);

								turSNJobItems.add(turSNJobItem);

								chunkTotal++;
								chunkCurrent++;
								if (chunkCurrent == chunk) {
									sendServer(turSNJobItems, chunkTotal);
									turSNJobItems = new TurSNJobItems();
									chunkCurrent = 0;

								}
							}
							return FileVisitResult.CONTINUE;
						}
					});

			if (chunkCurrent > 0) {

				this.sendServer(turSNJobItems, chunkTotal);
				turSNJobItems = new TurSNJobItems();
				chunkCurrent = 0;
			}
		} catch (IOException ioe) {
			logger.error(ioe);
		}

	}

	private static String cleanTextContent(String text) {
		text = text.replaceAll("[\r\n\t]", " ");
		text = text.replaceAll("[^\\p{L}&&[^0-9A-Za-z]&&[^\\p{javaSpaceChar}]&&[^\\p{Punct}]]", "").replaceAll("_{2,}",
				"");

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

	private String extractTextFromFile(File file) {
		TurFileAttributes turFileAttributes = readFile(file.getAbsolutePath());
		return cleanTextContent(turFileAttributes.getContent());
		// return turFileAttributes.getContent();
	}

	private String extractTextFromImage(File file)
			throws IOException, UnsupportedEncodingException, FileNotFoundException {
		StringBuffer strB = new StringBuffer();
		File outputFile = new File("/tmp/turing-filesystem-image");

		List<String> cmd = new ArrayList<String>();
		cmd.add("/usr/local/bin/tesseract");
		cmd.add(file.getName());
		cmd.add(outputFile.getAbsoluteFile().toString());

		ProcessBuilder pb = new ProcessBuilder();
		pb.directory(file.getParentFile());
		pb.command(cmd);

		pb.redirectErrorStream(true);
		Process process = pb.start();

		int w;
		try {
			w = process.waitFor();

			if (w == 0) {
				BufferedReader in = new BufferedReader(
						new InputStreamReader(new FileInputStream(outputFile.getAbsolutePath() + ".txt"), "UTF-8"));
				String str;
				while ((str = in.readLine()) != null) {
					strB.append(str).append(EOL);
				}
				in.close();
			} else {
				String msg = "";
				BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
				String line = null;
				while ((line = bufferedReader.readLine()) != null) {
					msg += line;
				}
				System.out.println(msg);
				bufferedReader.close();
				throw new RuntimeException(msg);
			}

			new File(outputFile.getAbsolutePath() + ".txt").delete();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return cleanTextContent(strB.toString());
		// return strB.toString();
	}
}
