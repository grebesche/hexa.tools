package com.hexa.client.ui.treetable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;

import com.google.gwt.core.client.JsArray;
import com.google.gwt.core.shared.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.ImageResource;
import com.google.gwt.uibinder.client.UiConstructor;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.ui.Panel;
import com.google.gwt.user.client.ui.Widget;
import com.hexa.client.tools.JQuery;
import com.hexa.client.ui.miracle.Printer;
import com.hexa.client.ui.treetable.event.TableCellClickEvent;
import com.hexa.client.ui.treetable.event.TableCellClickEvent.TableCellClickHandler;
import com.hexa.client.ui.treetable.event.TableHeaderClickEvent;
import com.hexa.client.ui.treetable.event.TableHeaderClickEvent.TableHeaderClickHandler;

public class TreeTable extends Panel
{
	interface BasicImageBundle extends ClientBundle
	{
		@Source( "16-arrow-down.png" )
		ImageResource treeMinus();

		@Source( "16-arrow-right.png" )
		ImageResource treePlus();

		@Source( "blank16.png" )
		ImageResource blank();
	}

	private static BasicImageBundle BUNDLE;

	private BasicImageBundle basicBundle()
	{
		if( BUNDLE == null )
			BUNDLE = GWT.create( BasicImageBundle.class );

		return BUNDLE;
	}

	final int treePadding = 25;

	final ImageResource treeMinus;
	final ImageResource treePlus;
	final ImageResource blankImage;

	// can be : sub item added, sub item removed, expanded, shrinked
	public interface IItemStateCallback
	{
		void onItemStateChange();
	}

	Element m_decorator;
	Element m_table;
	Element m_head;
	Element m_body;
	Element m_headerRow = null;
	Row m_rootItem = new Row(this);

	int m_nbColumns = 0;
	String[] m_headers = null;
	String m_rowTemplate = "";

	HashMap<Element, Widget> m_widgets = new HashMap<Element, Widget>();

	@UiConstructor
	public TreeTable( ImageResource treeMinus, ImageResource treePlus )
	{
		this( treeMinus, treePlus, null );
	}

	public TreeTable( ImageResource treeMinus, ImageResource treePlus, ImageResource blankImage )
	{
		if( treeMinus == null )
			treeMinus = basicBundle().treeMinus();
		if( treePlus == null )
			treePlus = basicBundle().treePlus();
		if( blankImage == null )
			blankImage = basicBundle().blank();

		this.treeMinus = treeMinus;
		this.treePlus = treePlus;
		this.blankImage = blankImage;

		m_decorator = DOM.createDiv();
		m_decorator.setClassName( "tableDecorator" );
		setElement( m_decorator );

		m_table = DOM.createTable();
		m_table.setClassName( "TreeTable" );
		m_decorator.appendChild( m_table );

		m_head = DOM.createTHead();
		m_table.appendChild( m_head );

		m_body = DOM.createTBody();
		m_table.appendChild( m_body );

		addDomHandler( new ClickHandler()
		{
			@Override
			public void onClick( ClickEvent event )
			{
				Element td = getEventTargetCell( Event.as( event.getNativeEvent() ) );
				if( td == null )
				{
					Element th = getEventTargetHeader( DOM.eventGetTarget( Event.as( event.getNativeEvent() ) ) );
					if( th == null )
						return;

					int column = DOM.getChildIndex( m_headerRow, th );

					fireEvent( new TableHeaderClickEvent( column, event ) );

					return;
				}

				Element tr = DOM.getParent( td );
				int column = DOM.getChildIndex( tr, td );

				Row item = (Row) tr.getPropertyObject( "linkedItem" );
				if( item != null )
				{
					// if hit the tree arrow (which is the <img> first child of <td>
					if( column==0 && event.getNativeEvent().getEventTarget().<Element> cast()==td.getFirstChildElement() )
						item.setExpanded( !item.getExpanded() );
					else
						fireEvent( new TableCellClickEvent( item, column, event ) );
				}
			}
		}, ClickEvent.getType() );
	}

	public TreeTable()
	{
		this( null, null, null );
	}

	public HandlerRegistration addTableHeaderClickHandler( TableHeaderClickHandler handler )
	{
		return addHandler( handler, TableHeaderClickEvent.getType() );
	}

	public HandlerRegistration addTableCellClickHandler( TableCellClickHandler handler )
	{
		return addHandler( handler, TableCellClickEvent.getType() );
	}

	@Override
	public void clear()
	{
		emptyTable();
	}

	public int getEventTargetHeaderIdx( Element th )
	{
		th = getEventTargetHeader( th );
		if( th == null )
			return -1;
		return DOM.getChildIndex( m_headerRow, th );
	}

	protected Element getEventTargetHeader( Element th )
	{
		for( ; th != null; th = DOM.getParent( th ) )
		{
			if( th.getTagName().equalsIgnoreCase( "th" ) )
			{
				Element head = DOM.getParent( th );
				if( head == m_headerRow )
					return th;
			}
			// If we run into this table's head, we're out of options.
			if( th == m_headerRow )
			{
				return null;
			}
		}
		return null;
	}

	protected Element getEventTargetCell( Event event )
	{
		Element td = DOM.eventGetTarget( event );
		for( ; td != null; td = DOM.getParent( td ) )
		{
			// If it's a TD, it might be the one we're looking for.
			if( td.getTagName().equalsIgnoreCase( "td" ) )
			{
				// Make sure it's directly a part of this table before returning
				// it
				Element tr = DOM.getParent( td );
				Element body = DOM.getParent( tr );
				if( body == m_body )
					return td;
			}

			// If we run into this table's body, we're out of options.
			if( td == m_body )
				return null;
		}

		return null;
	}

	public void emptyTable()
	{
		for( Entry<Element, Widget> entry : m_widgets.entrySet() )
			removeWidget( entry.getKey(), entry.getValue(), false );
		m_widgets.clear();

		m_rootItem = new Row(this);

		m_body.setInnerText( "" );
	}

	void clearCell( Element td )
	{
		clearCellWidget( td );
		clearCellText( td );
	}

	void clearCellWidget( Element td )
	{
		Widget old = m_widgets.get( td );
		if( old != null )
			removeWidget( td, old, true );
	}

	private void clearCellText( Element td )
	{
		td.setInnerText( "" );
	}

	void addWidget( Element td, Widget w )
	{
		// detach new child
		w.removeFromParent();

		// logical add
		m_widgets.put( td, w );

		// physical add
		td.appendChild( w.getElement() );

		// adopt
		adopt( w );
	}

	private void removeWidget( Element td, Widget w, boolean fDoLogical )
	{
		assert (td != null) && (w != null);

		// Validate.
		if( w.getParent() != this )
			return;

		try
		{
			orphan( w );
		}
		finally
		{
			// Physical detach.
			Element elem = w.getElement();
			elem.removeFromParent();

			// Logical detach.
			if( fDoLogical )
				m_widgets.remove( td );
		}
	}

	public Row getItemForRef( int ref )
	{
		JsArray<Element> rows = JQuery.get().jqSelect( "tr[ref=\"" + ref + "\"]", m_body );
		if( rows.length() != 1 )
			return null;

		Element tr = rows.get( 0 );
		if( tr == null )
			return null;

		Object item = tr.getPropertyObject( "linkedItem" );

		return (Row) item;
	}

	public ArrayList<Row> getItemChilds( Row item )
	{
		if( item == null )
			item = m_rootItem;
		return item.getChilds();
	}

	private class HeaderPrinter implements Printer
	{
		final int column;

		HeaderPrinter( int column )
		{
			this.column = column;
		}

		@Override
		public void setWidget( Widget widget )
		{
			assert false;
			setHeader( column, "WIDGET SHOULD BE HERE, NOT IMPLEMENTED" );
		}

		@Override
		public void setText( String text )
		{
			setHeader( column, text );
		}

		@Override
		public void setHTML( String html )
		{
			setHeader( column, html + " (html not implemented)" );
		}
	}

	public Printer getHeaderPrinter( int column )
	{
		return new HeaderPrinter( column );
	}

	public void setColumnWidth( int column, int width )
	{
		DOM.getChild( m_headerRow, column ).setAttribute( "width", width + "px" );
	}

	public void setHeader( int col, String header )
	{
		if( col >= m_nbColumns )
		{
			String[] newHeaders = new String[Math.max( col + 1, m_nbColumns )];
			for( int i = 0; i < m_nbColumns; i++ )
				newHeaders[i] = m_headers[i];
			m_headers = newHeaders;
		}

		m_headers[col] = header;

		setHeaders( m_headers );
	}

	public void setHeaders( String... headers )
	{
		Element oldHeaderRow = m_headerRow;

		m_headers = headers;

		m_headerRow = DOM.createTR();
		m_headerRow.setClassName( "thead" );
		StringBuilder b = new StringBuilder();
		StringBuilder bTemplate = new StringBuilder();
		m_nbColumns = headers.length;
		for( int i = 0; i < m_nbColumns; i++ )
		{
			b.append( "<th>" + headers[i] + "</th>" );
			bTemplate.append( "<td/>" );
		}

		m_headerRow.setInnerHTML( b.toString() );

		if( oldHeaderRow != null )
			m_head.replaceChild( m_headerRow, oldHeaderRow );
		else
			DOM.insertChild( m_head, m_headerRow, 0 );

		m_rowTemplate = bTemplate.toString();
	}

	public Row addRow()
	{
		return addRow( null );
	}

	// adds an item at the end of the children of the parent
	public Row addRow( Object parent )
	{
		Row parentItem = (Row) parent;
		if( parentItem == null )
			parentItem = m_rootItem;

		return parentItem.addLastChild();
	}

	@Override
	public Iterator<Widget> iterator()
	{
		return m_widgets.values().iterator();
	}

	@Override
	public boolean remove( Widget child )
	{
		return false;
	}
}
