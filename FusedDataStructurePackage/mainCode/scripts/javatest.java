class javatest {

public static void main(String[] args) {
	String property = System.getProperty("java.library.path");
	StringTokenizer parser = new StringTokenizer(property, ";");
	while (parser.hasMoreTokens()) {
	    System.err.println(parser.nextToken());
	}
}
}