package com.vitalsigns.demoholter;

import java.util.ArrayList;
import java.util.List;

public class RingBuffer <E> {
  private static final String LOG_TAG = "RingBuffer:";
  private final int capacity;
  private final Entry<E>[] data;
  private Entry<E> head = null;
  private int     dataCounter;
  private List<E> list;
  private int     dataCount;

  public RingBuffer(int fixedCapacity) {
    this.capacity = fixedCapacity;
    this.data = new Entry[capacity];
    list = new ArrayList<E>(capacity);
    init();
  }

  private synchronized void init() {
    dataCounter = 0;
    for (int i = 0; i < capacity; i++) {
      Entry<E> entry = new Entry<E>();
      data[i] = entry;
    }

    for (int i = 0; i < capacity; i++) {
      Entry<E> entry = data[i];

      if (i == capacity - 1) {
        entry.setFront(data[0]);
      } else {
        Entry<E> front = data[i + 1];
        entry.setFront(front);
      }
    }

    this.head = data[0];
  }

  public void add(E element) {
    head.setData(element);
    head = head.front;
    dataCounter++;
    if(dataCounter > capacity) {
      dataCounter = 1;
    }
  }

  public List<E> getList(int startIdx, int endIdx) {
    E value;

    list.clear();
    dataCount = 0;
    endIdx = (endIdx > dataCounter || endIdx == -1) ? dataCounter : endIdx;
    if(endIdx > startIdx) {
      for (int idx = startIdx; idx < endIdx; idx++) {
        value = data[idx].getData();
        if (value != null) {
          list.add(value);
          dataCount++;
        }
      }
    } else{
      for (int idx = startIdx; idx < capacity; idx++) {
        value = data[idx].getData();
        if (value != null) {
          list.add(value);
          dataCount++;
        }
      }
      for (int idx = 0; idx < endIdx; idx++) {
        value = data[idx].getData();
        if (value != null) {
          list.add(value);
          dataCount++;
        }
      }
    }
    return list;
  }

  public int getDataCount() {
    return dataCount;
  }

  public int getDataCounter()
  {
    return dataCounter;
  }

  public boolean isEmpty() {
    if(dataCounter == 0) {
      return true;
    } else {
      return false;
    }
  }

  public E getHead() {
    return head.getData();
  }

  private class Entry<E> {
    private Entry<E> front;
    private E data = null;

    Entry() {
      this.front = (Entry<E>) head;
    }

    E getData() {
      return data;
    }

    void setData(E e) {
      this.data = e;
    }

    private void setFront(Entry<E> front) {
      this.front = front;
    }
  }
}