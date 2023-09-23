import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:sms_advanced/sms_advanced.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatefulWidget {
  const MyApp({Key? key}) : super(key: key);

  @override
  State<MyApp> createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  final SmsQuery query = SmsQuery();
  List<SmsThread> threads = [];

  @override
  void initState() {
    super.initState();
    query.getAllThreads.then((value) {
      threads = value;
      setState(() {});
    });
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
        theme: ThemeData(
          primarySwatch: Colors.blue,
        ),
        home: Scaffold(
          appBar: AppBar(
            title: const Text("Example"),
          ),
          body: ListView.builder(
            itemCount: threads.length,
            itemBuilder: (BuildContext context, int index) {
              return Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  ListTile(
                    onTap: () {
                      Navigator.push(
                          context, MaterialPageRoute(builder: (context) => ListSmsPage(thread: threads[index])));
                    },
                    minVerticalPadding: 8,
                    minLeadingWidth: 4,
                    title: Text(threads[index].messages.last.body ?? 'empty'),
                    subtitle: Text(threads[index].contact?.address ?? 'empty'),
                  ),
                  const Divider()
                ],
              );
            },
          ),
        ));
  }
}

class ListSmsPage extends StatefulWidget {
  final SmsThread thread;

  const ListSmsPage({Key? key, required this.thread}) : super(key: key);

  @override
  State<ListSmsPage> createState() => _ListSmsPageState();
}

class _ListSmsPageState extends State<ListSmsPage> {
  final SmsQuery query = SmsQuery();
  final MmsReader mmsReader = MmsReader();
  List<SmsMessage> messages = [];

  @override
  void initState() {
    super.initState();
    query
        .querySms(
      threadId: widget.thread.threadId,
    )
        .then((value) {
      messages = value;
      setState(() {});
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: ListView.builder(
        itemCount: messages.length,
        itemBuilder: (BuildContext context, int index) {
          final msg = messages[index];
          return Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              ListTile(
                onTap: () {},
                minVerticalPadding: 8,
                minLeadingWidth: 4,
                title: Text(messages[index].body ?? 'empty'),
                subtitle: Text(messages[index].kind.toString() + " " + messages[index].contentType.toString()),
              ),
              if (msg.hasImage)
                FutureBuilder(
                  future: mmsReader.readMmsImage(msg.id!),
                  builder: (context, snapshot) {
                    if (snapshot.hasData) {
                      return Image.memory(snapshot.data as Uint8List);
                    }
                    return const SizedBox();
                  },
                ),
              const Divider(),
            ],
          );
        },
      ),
    );
  }
}
