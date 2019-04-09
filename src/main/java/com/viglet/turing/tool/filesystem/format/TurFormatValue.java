package com.viglet.turing.tool.filesystem.format;

import com.viglet.turing.tool.filesystem.TurFSImportTool;
import com.viglet.turing.util.HtmlManipulator;

public class TurFormatValue {
	TurFSImportTool turFSImportTool = null;
	public TurFormatValue(TurFSImportTool turFSImportTool) {
		this.turFSImportTool = turFSImportTool;
	}
	public String format(String name, String value) {
		String[] strHTMLFields = turFSImportTool.htmlField.toLowerCase().split(",");
		for (String strHTMLField : strHTMLFields) {
			if (name.toLowerCase().equals(strHTMLField.toLowerCase())) {
				if (name.toLowerCase().equals("id")) {
					this.idField(HtmlManipulator.html2Text(value));

				} else {
					return HtmlManipulator.html2Text(value);
				}
			}
		}
		if (name.toLowerCase().equals("id")) {
			return this.idField(value);
		} else {
			return value;
		}
	}

	public String idField(int idValue) {
		if (turFSImportTool.typeInId) {
			return turFSImportTool.type + idValue;
		} else {
			return Integer.toString(idValue);
		}
	}

	public String idField(String idValue) {
		if (turFSImportTool.typeInId) {
			return turFSImportTool.type + idValue;
		} else {
			return idValue;
		}
	}

}
