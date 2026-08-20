package com.tom.cpm.paper;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;

import com.tom.cpl.text.TextRemapper;
import com.tom.cpl.text.TextStyle;

public class TextComponents {
	private final I18n i18n;

	public TextComponents(File translationsFile) {
		I18n i18n = null;
		if(translationsFile.exists()) {
			try {
				i18n = I18n.loadLocaleData(new FileInputStream(translationsFile));
			} catch (IOException e) {
				CPMPaperPlugin.getLog().warn("Failed to load localization from cpm.lang", e);
			}
		}
		if(i18n == null) {
			try {
				i18n = I18n.loadLocaleData(TextComponents.class.getResourceAsStream("/assets/cpm/lang/en_us.lang"));
			} catch (IOException e) {
				CPMPaperPlugin.getLog().error("Failed to load localization from builtin lang file", e);
				i18n = new I18n() {
					@Override
					public String format(String translateKey, Object... parameters) {
						return "Server failed to load builtin localization. This is a BUG, please report it to the server owner.";
					}
				};
			}
		}
		this.i18n = i18n;
	}

	private Component translatable(String key, Object[] a) {
		ComponentLike[] args = new ComponentLike[a.length];
		Object[] fallback = new Object[a.length];
		for (int i = 0; i < a.length; i++) {
			Object object = a[i];
			if (object instanceof ComponentLike c) {
				args[i] = c;
				if (c instanceof TranslatableComponent tc) {
					fallback[i] = tc.fallback();
				} else {
					fallback[i] = c.toString();
				}
			} else {
				fallback[i] = object;
				args[i] = Component.text(String.valueOf(object));
			}
		}
		return Component.translatable(key, i18n.format(key, fallback), Style.empty(), args);
	}

	private Component applyStyle(Component a, TextStyle b) {
		var style = Style.style();
		if (b.bold)style.decorate(TextDecoration.BOLD);
		if (b.italic)style.decorate(TextDecoration.ITALIC);
		if (b.underline)style.decorate(TextDecoration.UNDERLINED);
		if (b.strikethrough)style.decorate(TextDecoration.STRIKETHROUGH);
		return a.style(style);
	}

	public TextRemapper<Component> remapper() {
		return new TextRemapper<>(this::translatable, Component::text, Component::append, Component::keybind, this::applyStyle);
	}

	public String format(String translateKey, Object... parameters) {
		return i18n.format(translateKey, parameters);
	}
}
