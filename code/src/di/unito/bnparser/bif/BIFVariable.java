/*
 * Encog(tm) Core v3.4 - Java Version
 * http://www.heatonresearch.com/encog/
 * https://github.com/encog/encog-java-core
 
 * Copyright 2008-2017 Heaton Research, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *   
 * For more information on Heaton Research copyrights, licenses 
 * and trademarks visit:
 * http://www.heatonresearch.com/copyright
 */
package di.unito.bnparser.bif;

import java.util.ArrayList;
import java.util.List;

/**
 * A BIF variable.
 */
public class BIFVariable {
	private String name;
	private List<String> options = new ArrayList<String>();
	/**
	 * @return the name
	 */
	public String getName() {
		return name;
	}
	/**
	 * @param name the name to set
	 */
	public void setName(String name) {
		name=upperize(name);
		this.name = name;
	}
	/**
	 * @return the options
	 */
	public List<String> getOptions() {
		return options;
	}
	/**
	 * @param options the options to set
	 */
	public void setOptions(List<String> options) {

		this.options = options;
	}
	public void addOption(String s) {
		s=upperize(s);
		this.options.add(s);
		
	}

	private String upperize(String name){
		return name.substring(0,1).toUpperCase()+name.substring(1);
	}
	
}
