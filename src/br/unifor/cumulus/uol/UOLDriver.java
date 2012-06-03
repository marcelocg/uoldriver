/*
 * Copyright (c) 2009-2010 Shanti Subramanyam, Akara Sucharitakul

 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */
package br.unifor.cumulus.uol;

import com.sun.faban.common.NameValuePair;
import com.sun.faban.common.Utilities;
import com.sun.faban.driver.*;
import com.sun.faban.driver.util.ContentSizeStats;

import javax.xml.xpath.XPathExpressionException;
import java.io.IOException;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

@BenchmarkDefinition(name = "UOL Workload", 
					 version = "0.1", 
					 metric = "req/s"
)
@BenchmarkDriver(name = "UOLDriver", 
				 threadPerScale = (float) 1, 
				 opsUnit = "requests", 
				 metric = "req/s", 
				 responseTimeUnit = TimeUnit.MILLISECONDS
)
@FixedTime( cycleType = CycleType.THINKTIME, 
			cycleTime = 5, 
			cycleDeviation = 20
)
//@MatrixMix(operations = { "Home", "Historico", "Extrato" }, 
//		   mix = { @Row({ 	0, 		80, 		20 }), //Home 
//				   @Row({ 	20, 	39, 		41 }), //Historico
//				   @Row({ 	60, 	19, 		21 }) }//Extrato
//)
@MatrixMix(operations = { "Historico" }, 
mix = { @Row({ 	100  }) }
)
public class UOLDriver {

	private DriverContext ctx;
	private HttpTransport http;
	private Logger log;
	private Random randomizer;
	private String url, rootUrl, loginUrl, homeUrl, historicoUrl, extratoUrl;
	private ContentSizeStats contentStats = null;
	private String[] matriculas;

	public UOLDriver() throws XPathExpressionException, ConfigurationException, IOException {

		setup();

		configureURLs();

		contentStats = new ContentSizeStats(ctx.getOperationCount());
		ctx.attachMetrics(contentStats);
		
		init();
	}

	private void setup() throws XPathExpressionException, ConfigurationException, IOException {
		ctx = DriverContext.getContext();
		log = ctx.getLogger();
		HttpTransport
				.setProvider("com.sun.faban.driver.transport.hc3.ApacheHC3Transport");
		http = HttpTransport.newInstance();
		StringBuilder urlBuilder = new StringBuilder();

		String s = ctx.getProperty("secure");
		if ("true".equalsIgnoreCase(s))
			urlBuilder.append("https://");
		else
			urlBuilder.append("http://");

		s = ctx.getXPathValue("/UOL/webServer/fa:hostConfig/fa:hostPorts");
		List<NameValuePair<Integer>> hostPorts = Utilities.parseHostPorts(s);
		// We currently only support a single host/port with this workload
		if (hostPorts.size() != 1)
		{
			log.log(Level.INFO, "HostPorts após o parse: " + hostPorts.toString());
			throw new ConfigurationException(
					"Only single host:port currently supported.");
		}
		NameValuePair<Integer> hostPort = hostPorts.get(0);
		urlBuilder.append(hostPort.name);
		if (hostPort.value != null)
			urlBuilder.append(':').append(hostPort.value);

		url = urlBuilder.toString();

		String response = http.fetchURL(url).toString();

		url = response.substring(response.indexOf("<frame name=\"main\" src=\"") + 24, response.indexOf("<noframes>") - 10);
		url = url.substring(0, url.indexOf("/oul/inicio.jsp"));

	}

	private String getURL(String URLName){
		String s = ctx.getProperty(URLName);
		return url + ( (s.charAt(0) == '/') ? "" : "/" ) + s;
	}

	private void configureURLs(){
		
		rootUrl 	 = getURL("rootPath");
		homeUrl 	 = getURL("homePath");
		historicoUrl = getURL("historicoPath");
		extratoUrl   = getURL("extratoPath");

	}

	public void init() throws IOException {
		matriculas = ctx.getProperty("matriculas").split(",");
		
		randomizer = new Random();
		String matricula = matriculas[randomizer.nextInt(matriculas.length)];

		String response = http.fetchURL(rootUrl).toString();
		log.log(Level.INFO, response);
		doLogin(matricula);
	}
	
	private String constructLoginPost(String matricula) {
        StringBuilder postString = new StringBuilder();
        
        postString.append("matricula=").append(matricula);
        postString.append("&senha=").append("11111111");
        
        return postString.toString();
    }
	
	public void doLogin(String matricula) throws IOException {
		
		log.log(Level.INFO, "Efetuando login...");
		
		String loginPost = constructLoginPost(matricula);
		String loginAction = url + "/oul/LogonSubmit.do?method=logon";
		StringBuilder response = http.fetchURL(loginAction, loginPost);
		log.log(Level.INFO, "[LOGIN] Response Code:" + http.getResponseCode());
		
		// log.log(Level.INFO, response.toString());
		
	}

	@BenchmarkOperation(name = "Home", 
						max90th = 250, // 250 millisec
						timing = Timing.AUTO)
	public void doHomePage() throws IOException {
		int size = http.readURL(homeUrl);
		if (ctx.isTxSteadyState())
			contentStats.sumContentSize[ctx.getOperationId()] += size;
	}

	@BenchmarkOperation(name = "Historico", 
						max90th = 250, // 250 millisec
						timing = Timing.AUTO)
	public void doHistoricoPage() throws IOException {
		
		StringBuilder response = http.fetchURL(historicoUrl);
		log.log(Level.INFO, response.toString());
		
		if (ctx.isTxSteadyState())
			contentStats.sumContentSize[ctx.getOperationId()] += response.length();
	}

	@BenchmarkOperation(name = "Extrato", 
						max90th = 250, // 250 millisec
						timing = Timing.AUTO)
	public void doExtratoPage() throws IOException {
		int size = http.readURL(extratoUrl);
		if (ctx.isTxSteadyState())
			contentStats.sumContentSize[ctx.getOperationId()] += size;
	}
}
