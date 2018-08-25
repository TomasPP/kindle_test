
import java.io.*;
import java.util.*;

import org.apache.http.*;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONObject;

public class KindleContentTest {

    @SuppressWarnings("WeakerAccess")
    public static final String ERROR_SUBSTRING = "\"success\":false";

    // max value is 20. or you get error. see notes.txt. amazon.com uses 18.
    @SuppressWarnings("WeakerAccess")
    public static final int MAX_LIST_COUNT = 18;

    public static void main(String[] args) throws IOException {
        System.out.println("args = " + Arrays.toString(args));
        System.out.println("Working Directory = " + System.getProperty("user.dir"));

        validateCommandLine(args);

        final String csrfToken = args[0];

        final String cookieFile = "headers.txt";
        final Header[] headers = loadHeaders(cookieFile);

//        for (int i = 0; i < 5; i++)
//        bulkDeleteKindleItems(headers, csrfToken);
        getKindleContentItems(headers, csrfToken, MAX_LIST_COUNT);

//        getKindleBooksOldAPI(headers);
//        deleteKindleBookOldAPI(headers);
    }

    @SuppressWarnings({"unused", "SameParameterValue"})
    private static void bulkDeleteKindleItems(Header[] headers, String csrfToken) throws IOException {
        String listJson = getKindleContentItems(headers, csrfToken, MAX_LIST_COUNT);
        if (!listJson.contains(ERROR_SUBSTRING)) {
//            listJson = new JSONObject(listJson).toString(4); //pretty print
            logToFile("list_log.txt", listJson);
            
            List<String> contentItemIds = extractKindleContentItemIds(listJson);
            if (!contentItemIds.isEmpty()) {

//                final int deleteCount = 2; // delete smaller portion
//                contentItemIds = contentItemIds.size() >= deleteCount ? contentItemIds.subList(0, deleteCount) : contentItemIds;

                final String deleteResponse = deleteKindleContentItems(headers, csrfToken, contentItemIds);
                logToFile("delete_log.txt", deleteResponse);

//amazon.com UI limits bulk delete size to 10. but on api level it accepts value 18 with no problem.
/*
                while (!contentItemIds.isEmpty()) {
                    final int bulkSize = contentItemIds.size() > DELETE_BULK_SIZE ? DELETE_BULK_SIZE : contentItemIds.size();
                    final List<String> bulk = contentItemIds.subList(0, bulkSize);
                    final String deleteResponse = deleteKindleContentItems(headers, bulk, csrfToken);
                    logToFile("delete_log.txt", deleteResponse);

                    if (deleteResponse.contains(ERROR_SUBSTRING)) {
                        break;
                    }
                    contentItemIds.removeAll(bulk);
                }
*/
            }
        }
    }    private static void logToFile(String fileName, String text) throws IOException {
        System.out.println(text);
        appendStringToFile(fileName, new Date() + " -----" + "\n" + text);
    }

    private static void validateCommandLine(String[] args) {
        if (args.length == 0 || args.length > 1) {
            System.out.println("Usage: ");
            System.out.println(KindleContentTest.class.getName() + " <csrfToken value>");
            System.exit(0);
        }
    }

    private static void appendStringToFile(String fileName, String text) throws IOException {
        PrintWriter out = null;
        try {
            out = new PrintWriter(new BufferedWriter(new FileWriter(fileName, true)));
            out.println(text);
        } finally {
            if (out != null) {
                out.close();
            }
        }
    }

    private static List<String> extractKindleContentItemIds(String listJson) throws IOException {
//        String deletedBooks = readFileToString("deleted_books.txt");
//        String json = readFileToString("book_list.json");
        
        JSONObject root = new JSONObject(listJson);
        final JSONArray items = root.getJSONObject("OwnershipData").getJSONArray("items");
        List<String> result = new ArrayList<String>();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.length(); i++)
        {
            final JSONObject item = items.getJSONObject(i);
            String contentId = item.getString("asin");
            String title = item.getString("title");

            if (title.startsWith("Instapaper") /*&& !deletedBooks.contains(contentId)*/ ) {
                sb.append(contentId).append(" ").append(title).append("\n");
                result.add(contentId);
            }
        }
        if (!result.isEmpty()) {
            logToFile("delete_log.txt", sb.toString());
        }
        return result;
    }

    private static String deleteKindleContentItems(Header[] headers, String csrfToken, List<String> contentItemIds) throws IOException {
        String url = "https://www.amazon.com/mn/dcw/myx/ajax-activity";
//        String url = "http://localhost:8080";

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < contentItemIds.size(); i++) {
            if (i > 0) {
                sb.append(",");
            }
            String contentItemId = contentItemIds.get(i);
            sb.append(String.format("\"%s\":{\"category\":\"KindlePDoc\"}", contentItemId));
        }

        String json = String.format("{\"param\":{\"DeleteContent\":{\"asinDetails\":{%s}}}}",
                sb);
        System.out.println("json = " + json);

        List<NameValuePair> params = new ArrayList<NameValuePair>();
        params.add(new BasicNameValuePair("data", json));
        params.add(new BasicNameValuePair("csrfToken", csrfToken));
        UrlEncodedFormEntity entity = new UrlEncodedFormEntity(params, Consts.UTF_8);

        return executeHttpPost(url, headers, entity);
    }

    private static String getKindleContentItems(Header[] headers, String csrfToken, @SuppressWarnings("SameParameterValue") int count) throws IOException {
        String url = "https://www.amazon.com/mn/dcw/myx/ajax-activity";
//        String url = "http://localhost:8080";

        String json = String.format("{\"param\":{\"OwnershipData\":{\"sortOrder\":\"DESCENDING\",\"sortIndex\":\"DATE\",\"startIndex\":0," +
                        "\"batchSize\":%d,\"contentType\":\"KindlePDoc\"," +
                //I am interested only in Instapaper related personal documents. Change your search phrase here.
                        "\"phrase\":\"instapaper\",\"itemStatus\":[\"Active\"],\"isExtendedMYK\":false}}}",
                count);
        System.out.println("json = " + json);

        List<NameValuePair> params = new ArrayList<NameValuePair>();
        params.add(new BasicNameValuePair("data", json));
        params.add(new BasicNameValuePair("csrfToken", csrfToken));
        UrlEncodedFormEntity entity = new UrlEncodedFormEntity(params, Consts.UTF_8);

        return executeHttpPost(url, headers, entity);
    }

    private static String executeHttpPost(String url, Header[] headers, HttpEntity entity) throws IOException {
        HttpPost request = null;
        CloseableHttpResponse response = null;
        CloseableHttpClient client = null;

        String result;
        try {
            request = new HttpPost(url);
            request.setHeaders(headers);
            request.setEntity(entity);

//        if (true) return;
            client = HttpClients.createDefault();
            response = client.execute(request);

            result = printResponse(response);
        } finally {
            close(request, response, client);
        }
        return result;
    }

    private static void close(HttpPost request, CloseableHttpResponse response, CloseableHttpClient client) throws IOException {
        if (response != null) {
            response.close();
        }
        if (request != null) {
            request.releaseConnection();
        }
        if (client != null) {
            client.close();
        }
    }

    //Older FIONA API. Does not work. Getting error that category (kindle_pdoc?) is not supported.
    @SuppressWarnings("unused")
    private static void deleteKindleBookOldAPI(Header[] headers) throws IOException {
        String url = "https://www.amazon.com/gp/digital/fiona/du/fiona-delete.html";
        //noinspection SpellCheckingInspection
        String testAsin = "AHIXK123456789QDG5IXANS5LO7WIMC";
        String json = "{" +
                "\"contentName:\":\""+ testAsin +"\"" +
                "\"sid\": \"192-2870048-2042810\",\"orderID\": \"\",\"isAjax\": 1,\"category\": \"kindle_pdoc\"" +
//                    "contentName:"+ testAsin +
//                    "\"category\":\"kindle_pdoc\""+
                "}";
        System.out.println("json = " + json);

        executeHttpPost(url, headers, new StringEntity(json));
    }

    @SuppressWarnings("unused")
    private static void getKindleBooksOldAPI(Header[] headers) throws IOException {
        //Old FIONA API. Still works for listing Kindle Cloud Storage.
        String url = "https://www.amazon.com/gp/digital/fiona/manage/features/order-history/ajax/queryPdocs.html";

        executeHttpGet(url, headers);
    }

    private static void executeHttpGet(String url, Header[] headers) throws IOException {
        HttpGet request = null;
        try {
            request = new HttpGet(url);

            request.setHeaders(headers);

//        if (true) return;
            HttpClient client = HttpClientBuilder.create().build();
            HttpResponse response = client.execute(request);

            printResponse(response);
        } finally {
            if (request != null) {
                request.releaseConnection();
            }
        }
    }

    private static String printResponse(HttpResponse response) throws IOException {
        String result = null;
        System.out.println("----------------------------------------");
        System.out.println(response.getStatusLine());
        System.out.println("----------------------------------------");

        HttpEntity entity = response.getEntity();

        if (entity != null) {
//            InputStream inputStream = entity.getContent();
//            String result = readIntoString(inputStream);

            result = EntityUtils.toString(entity);

            System.out.println("result = " + result);
        }
        return result;
    }

    @SuppressWarnings("unused")
    private static String readIntoString(InputStream inputStream) {
        Scanner s = new Scanner(inputStream).useDelimiter("\\A");
        return s.hasNext() ? s.next() : "";
    }

    @SuppressWarnings("unused")
    private static String readFileToString(String fileName) throws FileNotFoundException {
        Scanner s = new Scanner(new File(fileName)).useDelimiter("\\A");
        return s.hasNext() ? s.next() : "";
    }

    @SuppressWarnings({"ArraysAsListWithZeroOrOneArgument", "WeakerAccess"})
    public static final Set<String> IGNORED_REQUEST_HEADERS = new HashSet<String>(Arrays.asList(
            "Content-Length".toUpperCase() //calculated by apache http client so no need to explicitly set it.
    ));

    @SuppressWarnings("SameParameterValue")
    private static Header[] loadHeaders(String cookieFile) throws FileNotFoundException {
        List<Header> headers = new LinkedList<Header>();
        final Scanner scanner = new Scanner(new File(cookieFile));
        while(scanner.hasNextLine()){
            final String line = scanner.nextLine();

            final int pos = line.indexOf(":");
            String name = line.substring(0, pos);
            String value = line.substring(pos + 2);
            final Header header = new BasicHeader(name, value);
            if (!IGNORED_REQUEST_HEADERS.contains(name.toUpperCase())) {
                System.out.println("header = " + header);
                headers.add(header);
            }
            else {
                System.out.println("Ignoring header = " + header);
            }

        }
        scanner.close();
        return headers.toArray(new Header[0]);
    }

}