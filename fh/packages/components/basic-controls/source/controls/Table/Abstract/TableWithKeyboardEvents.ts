
import {FhContainer, HTMLFormComponent} from "fh-forms-handler";
import {TableFixedHeaderAndHorizontalScroll} from "./TableFixedHeaderAndHorizontalScroll";
import {TableRowOptimized} from "./../Optimized/TableRowOptimized";

abstract class TableWithKeyboardEvents extends TableFixedHeaderAndHorizontalScroll {

    /**
     * Rewrited logic
     */
        // Row selection
    protected readonly selectable: boolean = true;
    protected readonly onRowClick: any = null;
    protected ctrlIsPressed: any = false;
    protected rows: Array<TableRowOptimized> = [];
    protected multiselect:boolean = false;

    private keyEventTimer: any;                //timer identifier
    private doneEventInterval: number = 500;   //event delay in miliseconds

    constructor(componentObj: any, parent: HTMLFormComponent) {
        super(componentObj, parent);

        this.onRowClick = this.componentObj.onRowClick;
        this.selectable = this.componentObj.selectable || true;
        this.multiselect = this.componentObj.multiselect || false;
        this.rawValue = this.componentObj.rawValue || this.componentObj.selectedRowsNumbers || [];

    }

    protected initExtends(){
        super.initExtends();
        if (this.selectable && this.onRowClick) {
            this.table.addEventListener('keydown', this.tableKeydownEvent.bind(this));
            this.table.addEventListener('keyup', this.tableKeyupEvent.bind(this));
        }
        this.bindKeyboardEvents();
        this.table.addEventListener('mousedown', this.tableMousedownEvent.bind(this));
    }

    //Capture ctrl click for keyboard events
    private tableKeydownEvent(event) {
        if (event.which == "17") {
            this.ctrlIsPressed = true;
        }
    }


    //Realese ctrl click for keyboard events
    private  tableKeyupEvent(e) {
        this.ctrlIsPressed = false;
        if (e.which == 9 && $(document.activeElement).is(":input")) {
            let parent = $(document.activeElement).parents('tbody tr:not(.emptyRow)');
            if (parent && parent.length > 0) {
                parent.trigger('click');
            }
        }
    }

    tableMousedownEvent(event) {
        if (event.ctrlKey) {
            event.preventDefault();
        }
    }

    protected bindKeyboardEvents() {
        this.table.addEventListener('keydown', function (e) {
            if (document.activeElement == this.table) {
                if (e.which == 40) { // strzalka w dol
                    clearTimeout(this.keyEventTimer);
                    e.preventDefault();
                    let current = $(this.htmlElement).find('tbody tr.table-primary');
                    let next = null;

                    if (current.length == 0) {
                        next = $(this.htmlElement).find('tbody tr:not(.emptyRow)').first();
                    } else {
                        next = current.next('tr:not(.emptyRow)');
                    }
                    //If there isn't next element we go back to first one.
                    if (next && next.length == 0) {
                        next = $(this.htmlElement).find('tbody tr:not(.emptyRow)').first();
                    }

                    if (next && next.length > 0) {
                        current.removeClass('table-primary');
                        next.addClass('table-primary');
                        let offset = $(next).position().top;
                        if (this.fixedHeader) {
                            offset -= this.header.clientHeight;
                        }
                        $(this.component).scrollTop(offset - (this.component.clientHeight / 2));
                        this.keyEventTimer = setTimeout(function (elem) {
                            elem.trigger('click');
                        }, this.doneEventInterval, next);
                    }
                } else if (e.which == 38) { // strzalka w gore
                    e.preventDefault();
                    clearTimeout(this.keyEventTimer);
                    let current = $(this.htmlElement).find('tbody tr.table-primary');
                    let prev = null;

                    if (current.length == 0) {
                        prev = $(this.htmlElement).find('tbody tr:not(.emptyRow)').first();
                    } else {
                        prev = current.prev('tr:not(.emptyRow)');
                    }

                    if (prev && prev.length == 0) {
                        $(this.component).scrollTop(0);
                    } else if (prev && prev.length > 0) {
                        current.removeClass('table-primary');
                        prev.addClass('table-primary');
                        let offset = $(prev).position().top;
                        if (this.fixedHeader) {
                            offset -= this.header.clientHeight;
                        }
                        $(this.component).scrollTop(offset - (this.component.clientHeight / 2));
                        this.keyEventTimer = setTimeout(function (elem) {
                            elem.trigger('click');
                        }, this.doneEventInterval, prev);

                    }
                } else if (e.which == 33 || e.which == 36) { // pgup i home
                    e.preventDefault();

                    let first = $(this.htmlElement).find('tbody tr:not(.emptyRow)').first();

                    if (first && first.length > 0) {
                        $(this.component).scrollTop(0);
                        first.trigger('click');
                    }
                } else if (e.which == 34 || e.which == 35) { // pgdown i end
                    e.preventDefault();

                    let last = $(this.htmlElement).find('tbody tr:not(.emptyRow)').last();

                    if (last && last.length > 0) {
                        $(this.component).scrollTop($(last).position().top);
                        last.trigger('click');
                    }
                }
            }
        }.bind(this));
    }


    protected highlightSelectedRows() {
        let oldSelected = this.table.querySelectorAll('.table-primary');
        if (oldSelected && oldSelected.length) {
            [].forEach.call(oldSelected, function (row) {
                row.classList.remove('table-primary');
            }.bind(this));
        }
        (this.rawValue || []).forEach(function (value) {
            if (value != -1) {
                const row: TableRowOptimized = this.rows[parseInt(value)];
                row.highlightRow();

            }
        }.bind(this));
    };

    update(change) {
        super.update(change);
        if (change.changedAttributes) {
            $.each(change.changedAttributes, function (name, newValue) {
                switch (name) {
                    case 'selectedRowNumber':
                        this.rawValue = change.changedAttributes['selectedRowNumber'];
                        this.highlightSelectedRows();

                        break;
                }
            }.bind(this));
        }
    };

}

export {TableWithKeyboardEvents};
